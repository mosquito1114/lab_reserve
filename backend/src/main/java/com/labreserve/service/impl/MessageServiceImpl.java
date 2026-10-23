package com.labreserve.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labreserve.common.constant.RedisKeyConst;
import com.labreserve.common.enums.EmailState;
import com.labreserve.common.enums.MessageType;
import com.labreserve.common.enums.SensitiveType;
import com.labreserve.common.exception.BizException;
import com.labreserve.common.result.PageResult;
import com.labreserve.mapper.MessageMapper;
import com.labreserve.mapper.UserMapper;
import com.labreserve.model.dto.MessageDeliveryLogPageDTO;
import com.labreserve.model.dto.MessagePageDTO;
import com.labreserve.model.entity.Message;
import com.labreserve.model.entity.User;
import com.labreserve.model.vo.MessageDeliveryLogVO;
import com.labreserve.model.vo.MessageVO;
import com.labreserve.mq.EmailQueueProducer;
import com.labreserve.service.MessageService;
import com.labreserve.util.MailUtils;
import com.labreserve.util.RedisUtils;
import com.labreserve.util.SecurityUtils;
import com.labreserve.util.SensitiveUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 消息服务实现。
 *
 * <p>收件箱是「Redis ZSet 索引 + DB 正文」的组合：
 * ZSet 里的顺序就是展示顺序，正文、已读状态、邮件状态都在 {@code tb_message}。
 * 所以未读数用 Redis 计数器（快），列表/详情用 DB（准），两边靠本类的方法保持一致。
 */
@Slf4j
@Service
public class MessageServiceImpl implements MessageService {

    /** 带筛选条件时，最多扫描多少条收件箱记录（防止深翻页把 Redis 打满） */
    private static final int SCAN_LIMIT = 2000;

    /** 扫描窗口大小 */
    private static final int SCAN_WINDOW = 100;

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private EmailQueueProducer emailQueueProducer;

    @Autowired
    private MailUtils mailUtils;

    @Autowired
    private SensitiveUtils sensitiveUtils;

    @Autowired
    private SecurityUtils securityUtils;

    // ============================================================
    // 发消息
    // ============================================================

    @Override
    public Long send(Long receiverId, MessageType type, String title, String content, String bizType, Long bizId) {
        if (receiverId == null) {
            throw BizException.badRequest("接收人不能为空");
        }
        if (type == null) {
            throw BizException.badRequest("消息类型不能为空");
        }
        if (!StringUtils.hasText(title)) {
            throw BizException.badRequest("消息标题不能为空");
        }
        User receiver = userMapper.selectById(receiverId);
        if (receiver == null) {
            throw BizException.notFound("接收人不存在");
        }

        // 只有「用户开启了邮件通知」且「邮箱非空」才进邮件队列
        boolean needMail = Integer.valueOf(1).equals(receiver.getNotifyEmail())
                && StringUtils.hasText(receiver.getEmail());

        Message message = new Message();
        message.setReceiverId(receiverId);
        message.setType(type.name());
        message.setTitle(title);
        message.setContent(content == null ? "" : content);
        message.setBizType(bizType);
        message.setBizId(bizId);
        message.setEmailRequired(needMail ? 1 : 0);
        message.setEmailState(needMail ? EmailState.SENDING.name() : null);
        message.setIsRead(0);
        message.setIsDeleted(0);
        messageMapper.insert(message);

        String inboxKey = RedisKeyConst.inboxKey(receiverId);
        redisUtils.zAdd(inboxKey, String.valueOf(message.getId()), System.currentTimeMillis());
        redisUtils.increment(RedisKeyConst.inboxUnreadKey(receiverId));

        if (needMail) {
            try {
                emailQueueProducer.enqueue(message.getId());
            } catch (Exception e) {
                // 入队失败不影响站内信：把状态退回「未入队」，由消费者的兜底扫描补投
                log.error("[消息] 邮件入队失败 msgId={}，等待兜底补投：{}", message.getId(), e.getMessage());
                messageMapper.update(null, Wrappers.<Message>lambdaUpdate()
                        .eq(Message::getId, message.getId())
                        .set(Message::getEmailState, null)
                        .set(Message::getEmailLastError, "入队失败：" + e.getMessage()));
            }
        }

        log.info("[消息] 发送 id={}, receiverId={}, type={}, mail={}", message.getId(), receiverId, type.name(), needMail);
        return message.getId();
    }

    // ============================================================
    // 收件箱
    // ============================================================

    @Override
    public PageResult<MessageVO> page(MessagePageDTO dto) {
        Long userId = securityUtils.getUserId();
        String inboxKey = RedisKeyConst.inboxKey(userId);
        long pageNum = dto.safePageNum();
        long pageSize = dto.safePageSize();

        if (!hasFilter(dto)) {
            long total = redisUtils.zCard(inboxKey);
            List<String> ids = redisUtils.zPageDesc(inboxKey, pageNum, pageSize);
            List<Message> messages = loadByIds(ids, userId);
            return PageResult.of(messages.stream().map(this::toVO).toList(), total, pageNum, pageSize);
        }

        // 有筛选条件：按 ZSet 倒序窗口扫描后在内存里过滤（收件箱是个人数据，量级可控）
        long need = pageNum * pageSize;
        List<Message> matched = new ArrayList<>();
        int scanned = 0;
        while (scanned < SCAN_LIMIT) {
            List<String> chunk = redisUtils.zPageDesc(inboxKey, (long) scanned / SCAN_WINDOW + 1, SCAN_WINDOW);
            if (chunk.isEmpty()) {
                break;
            }
            List<Message> loaded = loadByIds(chunk, userId);
            Map<Long, Message> byId = new LinkedHashMap<>();
            loaded.forEach(item -> byId.put(item.getId(), item));
            for (String id : chunk) {
                Message message = byId.get(parseId(id));
                if (message != null && matches(message, dto)) {
                    matched.add(message);
                }
            }
            scanned += chunk.size();
            if (matched.size() >= need || chunk.size() < SCAN_WINDOW) {
                break;
            }
        }

        int from = (int) Math.min((pageNum - 1) * pageSize, matched.size());
        int to = (int) Math.min(from + pageSize, matched.size());
        List<MessageVO> records = matched.subList(from, to).stream().map(this::toVO).toList();
        return PageResult.of(records, matched.size(), pageNum, pageSize);
    }

    @Override
    public MessageVO getInfo(Long id) {
        Message message = requireOwn(id);
        if (!Integer.valueOf(1).equals(message.getIsRead())) {
            markRead(message);
            message.setIsRead(1);
        }
        return toVO(message);
    }

    @Override
    public long unreadCount() {
        Long userId = securityUtils.getUserId();
        String key = RedisKeyConst.inboxUnreadKey(userId);
        String cached = redisUtils.get(key);
        if (cached != null) {
            try {
                return Math.max(0L, Long.parseLong(cached));
            } catch (NumberFormatException e) {
                log.warn("[消息] 未读计数非法值 key={}, value={}，按库重建", key, cached);
            }
        }
        // 缓存缺失（Redis 被清过 / 首次访问）时按库重建
        long count = messageMapper.selectCount(Wrappers.<Message>lambdaQuery()
                .eq(Message::getReceiverId, userId)
                .eq(Message::getIsRead, 0)
                .eq(Message::getIsDeleted, 0));
        redisUtils.set(key, String.valueOf(count));
        return count;
    }

    @Override
    public void read(Long id) {
        Message message = requireOwn(id);
        if (Integer.valueOf(1).equals(message.getIsRead())) {
            return;
        }
        markRead(message);
    }

    @Override
    public void readAll() {
        Long userId = securityUtils.getUserId();
        int updated = messageMapper.update(null, Wrappers.<Message>lambdaUpdate()
                .eq(Message::getReceiverId, userId)
                .eq(Message::getIsRead, 0)
                .eq(Message::getIsDeleted, 0)
                .set(Message::getIsRead, 1));
        redisUtils.delete(RedisKeyConst.inboxUnreadKey(userId));
        log.info("[消息] 全部已读 userId={}, 影响 {} 条", userId, updated);
    }

    @Override
    public void delete(Long id) {
        Message message = requireOwn(id);
        messageMapper.update(null, Wrappers.<Message>lambdaUpdate()
                .eq(Message::getId, id)
                .set(Message::getIsDeleted, 1));
        redisUtils.zRemove(RedisKeyConst.inboxKey(message.getReceiverId()), String.valueOf(id));
        if (!Integer.valueOf(1).equals(message.getIsRead())) {
            decreaseUnread(message.getReceiverId());
        }
        log.info("[消息] 删除 id={}, receiverId={}", id, message.getReceiverId());
    }

    @Override
    public void clear() {
        Long userId = securityUtils.getUserId();
        int updated = messageMapper.update(null, Wrappers.<Message>lambdaUpdate()
                .eq(Message::getReceiverId, userId)
                .eq(Message::getIsDeleted, 0)
                .set(Message::getIsDeleted, 1));
        redisUtils.delete(RedisKeyConst.inboxKey(userId));
        redisUtils.delete(RedisKeyConst.inboxUnreadKey(userId));
        log.info("[消息] 一键清空 userId={}, 影响 {} 条", userId, updated);
    }

    // ============================================================
    // 邮件投递记录
    // ============================================================

    @Override
    public PageResult<MessageDeliveryLogVO> deliveryLogPage(MessageDeliveryLogPageDTO dto) {
        LambdaQueryWrapper<Message> wrapper = Wrappers.<Message>lambdaQuery()
                .eq(Message::getEmailRequired, 1)
                .eq(StringUtils.hasText(dto.getEmailState()), Message::getEmailState,
                        dto.getEmailState() == null ? null : dto.getEmailState().trim().toUpperCase())
                .ge(dto.getBeginTime() != null, Message::getCreatedAt, dto.getBeginTime())
                .le(dto.getEndTime() != null, Message::getCreatedAt, dto.getEndTime())
                .orderByDesc(Message::getId);

        if (StringUtils.hasText(dto.getReceiverKeyword())) {
            List<Long> receiverIds = userMapper.selectList(Wrappers.<User>lambdaQuery()
                            .like(User::getRealName, dto.getReceiverKeyword())
                            .or()
                            .like(User::getUsername, dto.getReceiverKeyword()))
                    .stream().map(User::getId).toList();
            if (receiverIds.isEmpty()) {
                return PageResult.empty(dto.safePageNum(), dto.safePageSize());
            }
            wrapper.in(Message::getReceiverId, receiverIds);
        }

        Page<Message> page = messageMapper.selectPage(dto.toMpPage(), wrapper);
        Map<Long, User> receivers = loadUsers(page.getRecords().stream()
                .map(Message::getReceiverId).filter(Objects::nonNull).distinct().toList());
        PageResult<MessageDeliveryLogVO> result = PageResult.of(page, message -> toDeliveryVO(message, receivers));
        return result;
    }

    @Override
    public void sendTestEmail(String to) {
        if (!mailUtils.isConfigured()) {
            throw BizException.badRequest("邮件服务未配置：请在 .env 里填写 MAIL_USERNAME 与 MAIL_PASSWORD（QQ 邮箱为 SMTP 授权码），改完重启后端");
        }
        String subject = "【" + mailUtils.fromName() + "】测试邮件";
        String text = "这是一封测试邮件，说明 SMTP 配置可用。" + System.lineSeparator()
                + "发送时间：" + LocalDateTime.now() + System.lineSeparator()
                + "—— 本邮件由系统自动发送，请勿直接回复。";
        try {
            mailUtils.send(to, subject, text);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw BizException.badRequest("邮件发送失败：" + rootMessage(e));
        }
    }

    @Override
    public int sendSystem(List<Long> receiverIds, String title, String content) {
        if (receiverIds == null || receiverIds.isEmpty()) {
            throw BizException.badRequest("接收人不能为空");
        }
        List<Long> distinctIds = receiverIds.stream().filter(Objects::nonNull).distinct().toList();
        int sent = 0;
        for (Long receiverId : distinctIds) {
            try {
                send(receiverId, MessageType.SYSTEM, title, content, null, null);
                sent++;
            } catch (BizException e) {
                // 单个接收人异常（如账号不存在）不影响其他人
                log.warn("[消息] 系统通知发送失败 receiverId={}：{}", receiverId, e.getMessage());
            }
        }
        log.info("[消息] 系统通知已发送 title={}, 成功 {}/{} 人", title, sent, distinctIds.size());
        return sent;
    }

    // ============================================================
    // 内部工具
    // ============================================================

    /** 只能操作自己的消息 */
    private Message requireOwn(Long id) {
        if (id == null) {
            throw BizException.badRequest("消息 ID 不能为空");
        }
        Message message = messageMapper.selectById(id);
        if (message == null || Integer.valueOf(1).equals(message.getIsDeleted())) {
            throw BizException.notFound("消息不存在");
        }
        if (!Objects.equals(message.getReceiverId(), securityUtils.getUserId())) {
            throw BizException.forbidden("无权查看他人的消息");
        }
        return message;
    }

    private void markRead(Message message) {
        int updated = messageMapper.update(null, Wrappers.<Message>lambdaUpdate()
                .eq(Message::getId, message.getId())
                .eq(Message::getIsRead, 0)
                .set(Message::getIsRead, 1));
        if (updated > 0) {
            decreaseUnread(message.getReceiverId());
        }
    }

    /** 未读数 -1，最小到 0（防止计数漂移成负数） */
    private void decreaseUnread(Long userId) {
        String key = RedisKeyConst.inboxUnreadKey(userId);
        long remaining = redisUtils.increment(key, -1);
        if (remaining < 0) {
            redisUtils.set(key, "0");
        }
    }

    private boolean hasFilter(MessagePageDTO dto) {
        return StringUtils.hasText(dto.getType())
                || dto.getIsRead() != null
                || dto.getBeginTime() != null
                || dto.getEndTime() != null;
    }

    private boolean matches(Message message, MessagePageDTO dto) {
        if (StringUtils.hasText(dto.getType()) && !dto.getType().trim().equalsIgnoreCase(message.getType())) {
            return false;
        }
        if (dto.getIsRead() != null && !dto.getIsRead().equals(message.getIsRead())) {
            return false;
        }
        if (dto.getBeginTime() != null && message.getCreatedAt() != null
                && message.getCreatedAt().isBefore(dto.getBeginTime())) {
            return false;
        }
        if (dto.getEndTime() != null && message.getCreatedAt() != null
                && message.getCreatedAt().isAfter(dto.getEndTime())) {
            return false;
        }
        return true;
    }

    /** 按 ZSet 里的 ID 顺序返回（DB 查询不保证顺序） */
    private List<Message> loadByIds(List<String> ids, Long userId) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> longIds = ids.stream().map(this::parseId).filter(Objects::nonNull).toList();
        if (longIds.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Message> byId = new LinkedHashMap<>();
        messageMapper.selectBatchIds(longIds).forEach(message -> {
            if (Integer.valueOf(1).equals(message.getIsDeleted())) {
                return;
            }
            if (userId != null && !Objects.equals(message.getReceiverId(), userId)) {
                return;
            }
            byId.put(message.getId(), message);
        });
        return ids.stream().map(this::parseId).map(byId::get).filter(Objects::nonNull).toList();
    }

    private Map<Long, User> loadUsers(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, User> map = new LinkedHashMap<>();
        userMapper.selectBatchIds(userIds).forEach(user -> map.put(user.getId(), user));
        return map;
    }

    private Long parseId(String raw) {
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private MessageVO toVO(Message message) {
        MessageVO vo = new MessageVO();
        vo.setId(message.getId());
        vo.setType(message.getType());
        MessageType type = MessageType.of(message.getType());
        vo.setTypeName(type == null ? message.getType() : type.getDescription());
        vo.setTitle(message.getTitle());
        vo.setContent(message.getContent());
        vo.setBizType(message.getBizType());
        vo.setBizId(message.getBizId());
        vo.setIsRead(message.getIsRead());
        vo.setCreatedAt(message.getCreatedAt());
        return vo;
    }

    private MessageDeliveryLogVO toDeliveryVO(Message message, Map<Long, User> receivers) {
        User receiver = receivers.get(message.getReceiverId());
        MessageDeliveryLogVO vo = new MessageDeliveryLogVO();
        vo.setMessageId(message.getId());
        vo.setReceiverId(message.getReceiverId());
        vo.setReceiverName(receiver == null ? null : receiver.getRealName());
        vo.setReceiverUsername(receiver == null ? null : receiver.getUsername());
        vo.setReceiverEmail(receiver == null || !StringUtils.hasText(receiver.getEmail())
                ? null
                : sensitiveUtils.mask(receiver.getEmail(), SensitiveType.EMAIL));
        vo.setTitle(message.getTitle());
        vo.setEmailState(message.getEmailState());
        vo.setEmailLastError(message.getEmailLastError());
        vo.setCreatedAt(message.getCreatedAt());
        vo.setUpdatedAt(message.getUpdatedAt());
        return vo;
    }

    private String rootMessage(Throwable e) {
        Throwable cursor = e;
        while (cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        return StringUtils.hasText(cursor.getMessage()) ? cursor.getMessage() : cursor.getClass().getSimpleName();
    }
}
