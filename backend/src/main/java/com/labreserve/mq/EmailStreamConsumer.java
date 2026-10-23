package com.labreserve.mq;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.labreserve.common.constant.RedisKeyConst;
import com.labreserve.common.enums.EmailState;
import com.labreserve.config.MailProperties;
import com.labreserve.mapper.MessageMapper;
import com.labreserve.mapper.UserMapper;
import com.labreserve.model.entity.Message;
import com.labreserve.model.entity.User;
import com.labreserve.util.MailUtils;
import com.labreserve.util.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 邮件投递消费者（Redis Stream 消费组 {@code mailer}）。
 *
 * <p>可靠性模型：
 * <ul>
 *   <li><b>正常投递</b>：{@code XREADGROUP} 取新消息 → 读 {@code tb_message} 渲染 → SMTP 发送 → {@code XACK}</li>
 *   <li><b>失败不 ACK</b>：消息留在 PEL（未确认列表），并回写 {@code email_last_error}</li>
 *   <li><b>重投</b>：定时 {@code XAUTOCLAIM}，闲置超过 {@code retry-min-idle-seconds} 的消息重新认领再发</li>
 *   <li><b>死信</b>：投递次数达到 {@code max-retry} 仍失败 → 写入 {@code stream:msg:email:dead} + {@code XACK} +
 *       回写 {@code email_state=DEAD}</li>
 * </ul>
 *
 * <p>{@code max-retry} 是<b>总投递次数上限（含首次）</b>：默认 3 表示首发 1 次 + 重试 2 次，
 * 第 3 次仍失败即进死信，与 {@code api.md} 的「重试 3 次后进死信」表述一致。
 *
 * <p>SMTP 未配置（{@code MAIL_USERNAME} 为空）时消费者不消费，消息留在 Stream 里等配置补齐，
 * 这样开发阶段不配邮箱也能把站内信功能跑通。
 */
@Slf4j
@Component
public class EmailStreamConsumer {

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private MailUtils mailUtils;

    @Autowired
    private MailProperties mailProperties;

    @Autowired
    private EmailQueueProducer emailQueueProducer;

    /** 消费者名：多实例时便于在 XPENDING 里区分是谁在处理 */
    private final String consumerName = buildConsumerName();

    /** 单次拉取的条数 */
    private static final int BATCH = 10;

    /** 单次巡检的 PEL 条数 */
    private static final int RECLAIM_BATCH = 20;

    /** SMTP 未配置只提示一次，避免刷日志 */
    private volatile boolean notConfiguredWarned = false;

    // ============================================================
    // 1. 正常消费
    // ============================================================

    @Scheduled(fixedDelayString = "${lab-reserve.mail.poll-interval-ms:2000}")
    public void poll() {
        if (!mailUtils.isConfigured()) {
            if (!notConfiguredWarned) {
                notConfiguredWarned = true;
                log.warn("[邮件] SMTP 未配置（MAIL_USERNAME 为空），消费者暂停；消息会留在 Stream 中等待配置补齐");
            }
            return;
        }
        notConfiguredWarned = false;
        emailQueueProducer.ensureGroup();

        List<MapRecord<String, Object, Object>> records;
        try {
            records = redisUtils.template().opsForStream().read(
                    Consumer.from(RedisKeyConst.STREAM_EMAIL_GROUP, consumerName),
                    StreamReadOptions.empty().count(BATCH),
                    StreamOffset.create(RedisKeyConst.STREAM_EMAIL, ReadOffset.lastConsumed()));
        } catch (Exception e) {
            log.warn("[邮件] 拉取消息失败：{}", e.getMessage());
            return;
        }
        if (records == null || records.isEmpty()) {
            return;
        }
        records.forEach(this::deliver);
    }

    // ============================================================
    // 2. 重投 / 死信 / 未入队兜底
    // ============================================================

    @Scheduled(fixedDelayString = "${lab-reserve.mail.reclaim-interval-ms:30000}")
    public void reclaim() {
        if (!mailUtils.isConfigured()) {
            return;
        }
        emailQueueProducer.ensureGroup();
        refillMissingEnqueues();

        PendingMessages pendings;
        try {
            pendings = redisUtils.template().opsForStream().pending(
                    RedisKeyConst.STREAM_EMAIL, RedisKeyConst.STREAM_EMAIL_GROUP, Range.unbounded(), RECLAIM_BATCH);
        } catch (Exception e) {
            log.warn("[邮件] 读取 PEL 失败：{}", e.getMessage());
            return;
        }
        if (pendings == null || pendings.isEmpty()) {
            return;
        }

        for (PendingMessage pending : pendings) {
            boolean exhausted = pending.getTotalDeliveryCount() >= mailProperties.getMaxRetry();
            // 死信分支不用等闲置时间，直接认领取回消息体
            long minIdleSeconds = exhausted ? 0L : mailProperties.getRetryMinIdleSeconds();
            List<MapRecord<String, Object, Object>> claimed;
            try {
                claimed = redisUtils.template().opsForStream().claim(
                        RedisKeyConst.STREAM_EMAIL, RedisKeyConst.STREAM_EMAIL_GROUP, consumerName,
                        Duration.ofSeconds(minIdleSeconds), pending.getId());
            } catch (Exception e) {
                log.warn("[邮件] 认领消息失败 recordId={}：{}", pending.getId(), e.getMessage());
                continue;
            }
            if (claimed == null || claimed.isEmpty()) {
                // 还没到重投时间
                continue;
            }
            for (MapRecord<String, Object, Object> record : claimed) {
                if (exhausted) {
                    moveToDead(record, pending.getTotalDeliveryCount());
                } else {
                    log.info("[邮件] 重投 msgId={}（第 {} 次投递）", parseMessageId(record), pending.getTotalDeliveryCount() + 1);
                    deliver(record);
                }
            }
        }
    }

    /**
     * 未入队兜底：消息落库超过 {@code enqueue-grace-seconds} 仍是「未入队」，
     * 说明当时写 DB 成功后 XADD 失败（或进程被杀），这里补投一次。
     *
     * <p>只捞 {@code email_state IS NULL} 的行，避免和 PEL 里的重投逻辑打架。
     */
    private void refillMissingEnqueues() {
        LocalDateTime deadline = LocalDateTime.now().minusSeconds(mailProperties.getEnqueueGraceSeconds());
        List<Message> missing = messageMapper.selectList(Wrappers.<Message>lambdaQuery()
                .eq(Message::getEmailRequired, 1)
                .isNull(Message::getEmailState)
                .eq(Message::getIsDeleted, 0)
                .lt(Message::getCreatedAt, deadline)
                .orderByAsc(Message::getId)
                .last("limit 20"));
        for (Message message : missing) {
            try {
                updateEmailState(message.getId(), EmailState.SENDING.name(), null);
                emailQueueProducer.enqueue(message.getId());
                log.info("[邮件] 补投未入队消息 msgId={}", message.getId());
            } catch (Exception e) {
                // 回滚状态，下一轮再试
                updateEmailState(message.getId(), null, truncate(e.getMessage(), 400));
                log.warn("[邮件] 补投失败 msgId={}：{}", message.getId(), e.getMessage());
            }
        }
    }

    // ============================================================
    // 3. 单条投递
    // ============================================================

    private void deliver(MapRecord<String, Object, Object> record) {
        Long messageId = parseMessageId(record);
        if (messageId == null) {
            log.warn("[邮件] 消息体缺少 msgId，丢弃 recordId={}", record.getId());
            ack(record.getId());
            return;
        }
        Message message = messageMapper.selectById(messageId);
        if (message == null || Integer.valueOf(1).equals(message.getIsDeleted())) {
            ack(record.getId());
            return;
        }
        // 已经是终态：重复入队/重复投递，直接确认
        if (EmailState.OK.name().equals(message.getEmailState())
                || EmailState.DEAD.name().equals(message.getEmailState())) {
            ack(record.getId());
            return;
        }
        User receiver = userMapper.selectById(message.getReceiverId());
        if (receiver == null || !StringUtils.hasText(receiver.getEmail())) {
            // 没有收件邮箱：标记为不需要邮件，避免无限重投
            updateEmailState(messageId, null, "收件人邮箱为空");
            messageMapper.update(null, Wrappers.<Message>lambdaUpdate()
                    .eq(Message::getId, messageId)
                    .set(Message::getEmailRequired, 0));
            log.warn("[邮件] 收件人邮箱为空，跳过 msgId={}", messageId);
            ack(record.getId());
            return;
        }

        try {
            mailUtils.send(receiver.getEmail(), renderSubject(message), renderBody(message, receiver));
            updateEmailState(messageId, EmailState.OK.name(), null);
            ack(record.getId());
        } catch (Exception e) {
            String error = truncate(rootMessage(e), 480);
            updateEmailState(messageId, EmailState.SENDING.name(), error);
            log.warn("[邮件] 投递失败 msgId={}：{}（留在 PEL 等待重投）", messageId, error);
            // 故意不 ACK：留给 reclaim() 重投，达到上限后进死信
        }
    }

    private void moveToDead(MapRecord<String, Object, Object> record, long deliveryCount) {
        Long messageId = parseMessageId(record);
        if (messageId == null) {
            ack(record.getId());
            return;
        }
        Message message = messageMapper.selectById(messageId);
        String error = message == null ? null : message.getEmailLastError();
        if (!StringUtils.hasText(error)) {
            error = "连续 " + deliveryCount + " 次投递失败";
        }
        emailQueueProducer.deadLetter(messageId, error);
        updateEmailState(messageId, EmailState.DEAD.name(), truncate(error, 480));
        ack(record.getId());
        log.error("[邮件] msgId={} 已转入死信，失败原因：{}", messageId, error);
    }

    // ============================================================
    // 工具方法
    // ============================================================

    private void ack(RecordId recordId) {
        try {
            redisUtils.template().opsForStream()
                    .acknowledge(RedisKeyConst.STREAM_EMAIL, RedisKeyConst.STREAM_EMAIL_GROUP, recordId);
        } catch (Exception e) {
            log.warn("[邮件] ACK 失败 recordId={}：{}", recordId, e.getMessage());
        }
    }

    private void updateEmailState(Long messageId, String state, String lastError) {
        messageMapper.update(null, Wrappers.<Message>lambdaUpdate()
                .eq(Message::getId, messageId)
                .set(Message::getEmailState, state)
                .set(Message::getEmailLastError, lastError));
    }

    private Long parseMessageId(MapRecord<String, Object, Object> record) {
        Object raw = record.getValue().get("msgId");
        if (raw == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(raw));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String renderSubject(Message message) {
        return "【" + mailUtils.fromName() + "】" + message.getTitle();
    }

    private String renderBody(Message message, User receiver) {
        StringBuilder sb = new StringBuilder();
        sb.append(receiver.getRealName()).append("，你好：").append(System.lineSeparator()).append(System.lineSeparator());
        sb.append(message.getContent()).append(System.lineSeparator()).append(System.lineSeparator());
        if (StringUtils.hasText(message.getBizType()) && message.getBizId() != null) {
            sb.append("关联业务：").append(message.getBizType()).append(" #").append(message.getBizId())
                    .append(System.lineSeparator());
        }
        sb.append("—— 本邮件由系统自动发送，请勿直接回复。").append(System.lineSeparator());
        return sb.toString();
    }

    private String rootMessage(Throwable e) {
        Throwable cursor = e;
        while (cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        String message = cursor.getMessage();
        return StringUtils.hasText(message) ? message : cursor.getClass().getSimpleName();
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    private String buildConsumerName() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            host = "unknown";
        }
        String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
        return "mailer-" + host + "-" + pid;
    }
}
