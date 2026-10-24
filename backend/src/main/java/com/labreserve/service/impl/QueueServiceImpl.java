package com.labreserve.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labreserve.common.constant.CommonConst;
import com.labreserve.common.constant.RedisKeyConst;
import com.labreserve.common.enums.MessageType;
import com.labreserve.common.exception.BizException;
import com.labreserve.common.result.PageResult;
import com.labreserve.config.QueueProperties;
import com.labreserve.mapper.CandidateNoticeLogMapper;
import com.labreserve.mapper.EquipmentMapper;
import com.labreserve.mapper.LaboratoryMapper;
import com.labreserve.mapper.QueueBanRecordMapper;
import com.labreserve.mapper.ReservationEquipmentMapper;
import com.labreserve.mapper.ReservationMapper;
import com.labreserve.mapper.ResourceQueueMapper;
import com.labreserve.mapper.TimeSlotMapper;
import com.labreserve.mapper.UserMapper;
import com.labreserve.model.dto.QueueCreateDTO;
import com.labreserve.model.dto.QueuePageDTO;
import com.labreserve.model.dto.QueuePositionDTO;
import com.labreserve.model.entity.CandidateNoticeLog;
import com.labreserve.model.entity.Equipment;
import com.labreserve.model.entity.Laboratory;
import com.labreserve.model.entity.QueueBanRecord;
import com.labreserve.model.entity.Reservation;
import com.labreserve.model.entity.ResourceQueue;
import com.labreserve.model.entity.TimeSlot;
import com.labreserve.model.entity.User;
import com.labreserve.model.vo.QueueBanVO;
import com.labreserve.model.vo.QueueConfirmVO;
import com.labreserve.model.vo.QueueNoticeVO;
import com.labreserve.model.vo.QueuePositionVO;
import com.labreserve.model.vo.QueueVO;
import com.labreserve.service.MessageService;
import com.labreserve.service.QueueCandidateService;
import com.labreserve.service.QueueService;
import com.labreserve.util.AfterCommitUtils;
import com.labreserve.util.RedisLockTemplate;
import com.labreserve.util.RedisUtils;
import com.labreserve.util.ReserveNoUtils;
import com.labreserve.util.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 排队与候补实现。
 */
@Slf4j
@Service
public class QueueServiceImpl implements QueueService {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    @Autowired
    private ResourceQueueMapper queueMapper;

    @Autowired
    private CandidateNoticeLogMapper noticeLogMapper;

    @Autowired
    private QueueBanRecordMapper queueBanRecordMapper;

    @Autowired
    private ReservationMapper reservationMapper;

    @Autowired
    private ReservationEquipmentMapper reservationEquipmentMapper;

    @Autowired
    private LaboratoryMapper laboratoryMapper;

    @Autowired
    private EquipmentMapper equipmentMapper;

    @Autowired
    private TimeSlotMapper timeSlotMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private QueueCandidateService queueCandidateService;

    @Autowired
    private RedisLockTemplate redisLockTemplate;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private ReserveNoUtils reserveNoUtils;

    @Autowired
    private MessageService messageService;

    @Autowired
    private AfterCommitUtils afterCommitUtils;

    @Autowired
    private QueueProperties queueProperties;

    @Autowired
    private SecurityUtils securityUtils;

    // ============================================================
    // 入队
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QueueVO create(QueueCreateDTO dto) {
        Long userId = securityUtils.getUserId();
        Long resourceId = Integer.valueOf(1).equals(dto.getResourceType()) ? dto.getLabId() : dto.getEquipmentId();
        if (resourceId == null) {
            throw BizException.badRequest(Integer.valueOf(1).equals(dto.getResourceType())
                    ? "实验室 ID 不能为空" : "设备 ID 不能为空");
        }
        if (Integer.valueOf(1).equals(dto.getResourceType())
                && !CommonConst.ROLE_TEACHER.equals(securityUtils.getRoleCode())) {
            throw BizException.conflict("实验室由教师为班级排课，学生无需排队",
                    Map.of("conflictCode", "LAB_RESERVE_TEACHER_ONLY", "canQueue", false));
        }
        // 资源与时段存在性
        String resourceNo = resolveResourceNo(dto.getResourceType(), resourceId);
        TimeSlot slot = timeSlotMapper.selectById(dto.getSlotId());
        if (slot == null || !Integer.valueOf(1).equals(slot.getStatus())) {
            throw BizException.badRequest("时段不存在或已停用");
        }
        if (dto.getReserveDate().isBefore(LocalDate.now())) {
            throw BizException.badRequest("预约日期不能早于今天");
        }
        // 禁入
        QueueBanRecord ban = activeBan(userId, dto.getResourceType(), dto.getLabId(), dto.getEquipmentId());
        if (ban != null) {
            throw BizException.conflict("你已被限制加入该资源排队，解除时间 "
                            + ban.getEndTime().format(DateTimeFormatter.ofPattern(CommonConst.DATETIME_PATTERN)),
                    Map.of("conflictCode", "QUEUE_BANNED", "canQueue", false, "banEndTime", ban.getEndTime()));
        }
        // 有位置就不该排队
        if (!slotOccupied(dto.getResourceType(), resourceId, dto.getReserveDate(), dto.getSlotId())) {
            throw BizException.conflict("该时段当前可直接预约，无需排队",
                    Map.of("conflictCode", "SLOT_AVAILABLE", "canQueue", false));
        }

        String lockKey = RedisKeyConst.lockQueueKey(dto.getResourceType(), resourceId,
                dto.getReserveDate(), dto.getSlotId());
        Long queueId = redisLockTemplate.execute(lockKey, () -> {
            ResourceQueue exists = findActiveQueue(userId, dto.getResourceType(), resourceId,
                    dto.getReserveDate(), dto.getSlotId());
            if (exists != null) {
                long position = positionOf(dto.getResourceType(), resourceId, dto.getReserveDate(),
                        dto.getSlotId(), userId);
                throw BizException.conflict("你已在队列中，当前第 " + position + " 位",
                        Map.of("conflictCode", "ALREADY_QUEUED", "position", position));
            }
            LocalDateTime now = LocalDateTime.now();
            String queueKey = RedisKeyConst.queueKey(dto.getResourceType(), resourceId,
                    dto.getReserveDate(), dto.getSlotId());
            // ZADD NX：同一用户同一时段只会有一个成员
            boolean added = redisUtils.zAddIfAbsent(queueKey, String.valueOf(userId), toEpochMillis(now));

            ResourceQueue entity = new ResourceQueue();
            entity.setQueueNo(nextQueueNo(resourceNo, dto.getReserveDate()));
            entity.setResourceType(dto.getResourceType());
            entity.setLabId(Integer.valueOf(1).equals(dto.getResourceType()) ? resourceId : null);
            entity.setEquipmentId(Integer.valueOf(2).equals(dto.getResourceType()) ? resourceId : null);
            entity.setReserveDate(dto.getReserveDate());
            entity.setSlotId(dto.getSlotId());
            entity.setApplicantId(userId);
            entity.setStatus(ResourceQueue.QUEUING);
            entity.setJoinTime(now);
            queueMapper.insert(entity);
            if (!added) {
                // ZSet 里已存在（极端并发下的重复提交），以 DB 为准，直接返回
                log.warn("[排队] ZSet 已存在成员，按重复入队处理 userId={}, key={}", userId, queueKey);
            }
            log.info("[排队] 入队 queueId={}, no={}, userId={}, type={}, resId={}, date={}, slot={}",
                    entity.getId(), entity.getQueueNo(), userId, dto.getResourceType(),
                    resourceId, dto.getReserveDate(), dto.getSlotId());
            return entity.getId();
        });
        return getInfo(queueId);
    }

    // ============================================================
    // 查询
    // ============================================================

    @Override
    public PageResult<QueueVO> myPage(QueuePageDTO dto) {
        Long userId = securityUtils.getUserId();
        LambdaQueryWrapper<ResourceQueue> wrapper = baseWrapper(dto).eq(ResourceQueue::getApplicantId, userId);
        Page<ResourceQueue> page = queueMapper.selectPage(dto.toMpPage(), wrapper);
        return PageResult.of(toVOList(page.getRecords()), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PageResult<QueueVO> page(QueuePageDTO dto) {
        if (dto.getResourceType() == null || dto.getReserveDate() == null || dto.getSlotId() == null) {
            throw BizException.badRequest("查看某个资源的队列需要传 resourceType / reserveDate / slotId");
        }
        Page<ResourceQueue> page = queueMapper.selectPage(dto.toMpPage(), baseWrapper(dto));
        return PageResult.of(toVOList(page.getRecords()), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public QueueVO getInfo(Long id) {
        ResourceQueue entity = requireExists(id);
        Long userId = securityUtils.getUserId();
        if (!Objects.equals(entity.getApplicantId(), userId) && !securityUtils.hasRole("LAB_ADMIN")
                && !securityUtils.hasRole("SYS_ADMIN")) {
            throw BizException.forbidden("无权查看他人的排队记录");
        }
        return toVOList(List.of(entity)).get(0);
    }

    @Override
    public QueuePositionVO myPosition(QueuePositionDTO dto) {
        Long userId = securityUtils.getUserId();
        Long resourceId = Integer.valueOf(1).equals(dto.getResourceType()) ? dto.getLabId() : dto.getEquipmentId();
        QueuePositionVO vo = new QueuePositionVO();
        vo.setQueued(false);
        if (resourceId == null) {
            return vo;
        }
        ResourceQueue entity = findActiveQueue(userId, dto.getResourceType(), resourceId,
                dto.getReserveDate(), dto.getSlotId());
        if (entity == null) {
            return vo;
        }
        String queueKey = RedisKeyConst.queueKey(dto.getResourceType(), resourceId,
                dto.getReserveDate(), dto.getSlotId());
        vo.setQueued(true);
        vo.setQueueId(entity.getId());
        vo.setQueueNo(entity.getQueueNo());
        vo.setStatus(entity.getStatus());
        long rank = redisUtils.zRank(queueKey, String.valueOf(userId));
        vo.setPosition(rank < 0 ? null : (int) rank + 1);
        vo.setTotalCount(redisUtils.zCard(queueKey));
        vo.setRemainSeconds(remainSeconds(entity.getId()));
        return vo;
    }

    @Override
    public List<QueueNoticeVO> noticeList(Long queueId) {
        ResourceQueue queue = requireExists(queueId);
        Long userId = securityUtils.getUserId();
        if (!Objects.equals(queue.getApplicantId(), userId) && !securityUtils.hasRole("LAB_ADMIN")
                && !securityUtils.hasRole("SYS_ADMIN")) {
            throw BizException.forbidden("无权查看他人的候补通知");
        }
        return noticeLogMapper.selectList(Wrappers.<CandidateNoticeLog>lambdaQuery()
                        .eq(CandidateNoticeLog::getQueueId, queueId)
                        .orderByAsc(CandidateNoticeLog::getSeq))
                .stream().map(item -> {
                    QueueNoticeVO vo = new QueueNoticeVO();
                    vo.setId(item.getId());
                    vo.setQueueId(item.getQueueId());
                    vo.setSeq(item.getSeq());
                    vo.setNoticeTime(item.getNoticeTime());
                    vo.setDeadline(item.getDeadline());
                    vo.setChannelName("站内信+邮件");
                    vo.setAction(item.getAction());
                    vo.setActionName(actionName(item.getAction()));
                    vo.setActionTime(item.getActionTime());
                    return vo;
                }).toList();
    }

    // ============================================================
    // 取消 / 确认 / 放弃
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        ResourceQueue queue = requireOwn(id);
        if (!ResourceQueue.QUEUING.equals(queue.getStatus())) {
            throw BizException.conflict("只有排队中的记录可以取消；已被通知候补的请使用「放弃」");
        }
        String lockKey = lockKeyOf(queue);
        redisLockTemplate.execute(lockKey, () -> {
            int changed = queueMapper.update(null, Wrappers.<ResourceQueue>lambdaUpdate()
                    .eq(ResourceQueue::getId, queue.getId())
                    .eq(ResourceQueue::getStatus, ResourceQueue.QUEUING)
                    .set(ResourceQueue::getStatus, ResourceQueue.CANCELED));
            if (changed == 0) {
                throw BizException.conflict("该排队记录状态已变化，请刷新后重试");
            }
            redisUtils.zRemove(RedisKeyConst.queueKey(queue.getResourceType(), queue.mainResourceId(),
                    queue.getReserveDate(), queue.getSlotId()), String.valueOf(queue.getApplicantId()));
        });
        // 取消不写禁入（需求 Q17），只提示后位顺位前移
        notifyBehindPositionShift(queue);
        log.info("[排队] 取消 queueId={}, userId={}", queue.getId(), queue.getApplicantId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QueueConfirmVO confirm(Long id) {
        ResourceQueue queue = requireOwn(id);
        if (!ResourceQueue.NOTIFIED.equals(queue.getStatus())) {
            throw BizException.conflict("当前没有待确认的候补机会（状态：" + statusName(queue.getStatus()) + "）");
        }
        String lockKey = lockKeyOf(queue);
        return redisLockTemplate.execute(lockKey, () -> {
            LocalDateTime now = LocalDateTime.now();
            // 1) 通知记录 CAS：0 未响应 → 1 确认（已被超时任务置 3 时 updated=0）
            int noticeChanged = noticeLogMapper.update(null, Wrappers.<CandidateNoticeLog>lambdaUpdate()
                    .eq(CandidateNoticeLog::getQueueId, queue.getId())
                    .eq(CandidateNoticeLog::getAction, CandidateNoticeLog.ACTION_PENDING)
                    .set(CandidateNoticeLog::getAction, CandidateNoticeLog.ACTION_CONFIRMED)
                    .set(CandidateNoticeLog::getActionTime, now));
            if (noticeChanged == 0) {
                throw BizException.conflict("该候补机会已失效（可能已超时）");
            }
            // 2) 排队状态 CAS：NOTIFIED → CONFIRMED
            int changed = queueMapper.update(null, Wrappers.<ResourceQueue>lambdaUpdate()
                    .eq(ResourceQueue::getId, queue.getId())
                    .eq(ResourceQueue::getStatus, ResourceQueue.NOTIFIED)
                    .set(ResourceQueue::getStatus, ResourceQueue.CONFIRMED));
            if (changed == 0) {
                throw BizException.conflict("该候补机会已失效（状态已变化）");
            }
            // 3) 时段是否仍空闲（锁内再确认一次）
            if (slotOccupied(queue.getResourceType(), queue.mainResourceId(),
                    queue.getReserveDate(), queue.getSlotId())) {
                queueMapper.update(null, Wrappers.<ResourceQueue>lambdaUpdate()
                        .eq(ResourceQueue::getId, queue.getId())
                        .set(ResourceQueue::getStatus, ResourceQueue.TIMEOUT));
                redisUtils.zRemove(RedisKeyConst.QUEUE_NOTICE_DEADLINE, String.valueOf(queue.getId()));
                throw BizException.conflict("该时段已被他人占用，候补转正失败");
            }
            // 4) 直接生成 APPROVED 预约单（需求 Q16）
            Reservation reservation = new Reservation();
            reservation.setReserveNo(reserveNoUtils.next(queue.getReserveDate()));
            reservation.setApplicantId(queue.getApplicantId());
            reservation.setResourceType(queue.getResourceType());
            reservation.setLabId(queue.getLabId());
            reservation.setEquipmentId(queue.getEquipmentId());
            reservation.setEquipmentCount(1);
            reservation.setReserveDate(queue.getReserveDate());
            reservation.setSlotId(queue.getSlotId());
            reservation.setUsePeople(1);
            reservation.setExperimentTypeId(resolveExperimentTypeId(queue));
            reservation.setCourseName(null);
            reservation.setPurpose("候补确认转正（排队号 " + queue.getQueueNo() + "）");
            reservation.setStatus("APPROVED");
            reservation.setAuditTime(now);
            reservation.setFromQueue(1);
            reservation.setSourceQueueId(queue.getId());
            reservationMapper.insert(reservation);

            // 5) 出队
            redisUtils.zRemove(RedisKeyConst.queueKey(queue.getResourceType(), queue.mainResourceId(),
                    queue.getReserveDate(), queue.getSlotId()), String.valueOf(queue.getApplicantId()));
            redisUtils.zRemove(RedisKeyConst.QUEUE_NOTICE_DEADLINE, String.valueOf(queue.getId()));

            QueueConfirmVO vo = new QueueConfirmVO();
            vo.setReservationId(reservation.getId());
            vo.setReserveNo(reservation.getReserveNo());
            vo.setStatus("APPROVED");
            vo.setMessage("确认成功，已为你生成预约单");
            log.info("[排队] 候补确认 queueId={}, reservationId={}, no={}",
                    queue.getId(), reservation.getId(), reservation.getReserveNo());

            // 6) 通知队内其他人顺位前移
            notifyBehindPositionShift(queue);
            return vo;
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void abandon(Long id, String reason) {
        ResourceQueue queue = requireOwn(id);
        if (!ResourceQueue.NOTIFIED.equals(queue.getStatus())) {
            throw BizException.conflict("只有待确认的候补可以放弃（状态：" + statusName(queue.getStatus()) + "）");
        }
        String lockKey = lockKeyOf(queue);
        redisLockTemplate.execute(lockKey, () -> {
            LocalDateTime now = LocalDateTime.now();
            int noticeChanged = noticeLogMapper.update(null, Wrappers.<CandidateNoticeLog>lambdaUpdate()
                    .eq(CandidateNoticeLog::getQueueId, queue.getId())
                    .eq(CandidateNoticeLog::getAction, CandidateNoticeLog.ACTION_PENDING)
                    .set(CandidateNoticeLog::getAction, CandidateNoticeLog.ACTION_ABANDONED)
                    .set(CandidateNoticeLog::getActionTime, now));
            if (noticeChanged == 0) {
                throw BizException.conflict("该候补机会已失效（可能已超时）");
            }
            int changed = queueMapper.update(null, Wrappers.<ResourceQueue>lambdaUpdate()
                    .eq(ResourceQueue::getId, queue.getId())
                    .eq(ResourceQueue::getStatus, ResourceQueue.NOTIFIED)
                    .set(ResourceQueue::getStatus, ResourceQueue.ABANDONED));
            if (changed == 0) {
                throw BizException.conflict("该候补机会已失效（状态已变化）");
            }
            redisUtils.zRemove(RedisKeyConst.queueKey(queue.getResourceType(), queue.mainResourceId(),
                    queue.getReserveDate(), queue.getSlotId()), String.valueOf(queue.getApplicantId()));
            redisUtils.zRemove(RedisKeyConst.QUEUE_NOTICE_DEADLINE, String.valueOf(queue.getId()));
            queueCandidateService.ban(queue.getApplicantId(), queue,
                    StringUtils.hasText(reason) ? reason : "主动放弃候补");
        });
        afterCommitUtils.run(() -> messageService.send(queue.getApplicantId(),
                MessageType.CANDIDATE_NOTICE, "已放弃候补",
                "你已放弃候补，将进入 " + queueProperties.getBanDays() + " 天排队禁入。", "QUEUE", queue.getId()));
        // 顺延下一位
        queueCandidateService.triggerCandidate(queue.getResourceType(), queue.mainResourceId(),
                queue.getReserveDate(), queue.getSlotId());
        log.info("[排队] 放弃候补 queueId={}, userId={}", queue.getId(), queue.getApplicantId());
    }

    // ============================================================
    // 禁入记录
    // ============================================================

    @Override
    public List<QueueBanVO> myBanList(Boolean activeOnly) {
        Long userId = securityUtils.getUserId();
        LambdaQueryWrapper<QueueBanRecord> wrapper = Wrappers.<QueueBanRecord>lambdaQuery()
                .eq(QueueBanRecord::getUserId, userId)
                .gt(Boolean.TRUE.equals(activeOnly), QueueBanRecord::getEndTime, LocalDateTime.now())
                .orderByDesc(QueueBanRecord::getId);
        List<QueueBanRecord> list = queueBanRecordMapper.selectList(wrapper);
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, String> labNames = loadLabNames(list);
        Map<Long, String> equipNames = loadEquipmentNames(list);
        LocalDateTime now = LocalDateTime.now();
        return list.stream().map(item -> {
            QueueBanVO vo = new QueueBanVO();
            vo.setId(item.getId());
            vo.setResourceType(item.getResourceType());
            vo.setResourceName(Integer.valueOf(1).equals(item.getResourceType())
                    ? labNames.getOrDefault(item.getLabId(), "实验室 #" + item.getLabId())
                    : equipNames.getOrDefault(item.getEquipmentId(), "设备 #" + item.getEquipmentId()));
            vo.setReason(item.getReason());
            vo.setStartTime(item.getStartTime());
            vo.setEndTime(item.getEndTime());
            vo.setActive(item.getEndTime() != null && item.getEndTime().isAfter(now));
            return vo;
        }).toList();
    }

    // ============================================================
    // 内部工具
    // ============================================================

    private LambdaQueryWrapper<ResourceQueue> baseWrapper(QueuePageDTO dto) {
        return Wrappers.<ResourceQueue>lambdaQuery()
                .eq(dto.getResourceType() != null, ResourceQueue::getResourceType, dto.getResourceType())
                .eq(dto.getLabId() != null, ResourceQueue::getLabId, dto.getLabId())
                .eq(dto.getEquipmentId() != null, ResourceQueue::getEquipmentId, dto.getEquipmentId())
                .eq(dto.getReserveDate() != null, ResourceQueue::getReserveDate, dto.getReserveDate())
                .eq(dto.getSlotId() != null, ResourceQueue::getSlotId, dto.getSlotId())
                .eq(StringUtils.hasText(dto.getStatus()), ResourceQueue::getStatus, dto.getStatus())
                .orderByAsc(ResourceQueue::getJoinTime)
                .orderByAsc(ResourceQueue::getId);
    }

    private ResourceQueue requireExists(Long id) {
        ResourceQueue queue = id == null ? null : queueMapper.selectById(id);
        if (queue == null) {
            throw BizException.notFound("排队记录不存在");
        }
        return queue;
    }

    private ResourceQueue requireOwn(Long id) {
        ResourceQueue queue = requireExists(id);
        if (!Objects.equals(queue.getApplicantId(), securityUtils.getUserId())) {
            throw BizException.forbidden("只能操作自己的排队记录");
        }
        return queue;
    }

    private ResourceQueue findActiveQueue(Long userId, Integer resourceType, Long resourceId,
                                          LocalDate date, Long slotId) {
        return queueMapper.selectOne(Wrappers.<ResourceQueue>lambdaQuery()
                .eq(ResourceQueue::getApplicantId, userId)
                .eq(ResourceQueue::getResourceType, resourceType)
                .eq(ResourceQueue::getReserveDate, date)
                .eq(ResourceQueue::getSlotId, slotId)
                .eq(Integer.valueOf(1).equals(resourceType), ResourceQueue::getLabId, resourceId)
                .eq(Integer.valueOf(2).equals(resourceType), ResourceQueue::getEquipmentId, resourceId)
                .in(ResourceQueue::getStatus, ResourceQueue.QUEUING, ResourceQueue.NOTIFIED)
                .orderByAsc(ResourceQueue::getId)
                .last("limit 1"));
    }

    private QueueBanRecord activeBan(Long userId, Integer resourceType, Long labId, Long equipmentId) {
        return queueBanRecordMapper.selectOne(Wrappers.<QueueBanRecord>lambdaQuery()
                .eq(QueueBanRecord::getUserId, userId)
                .eq(QueueBanRecord::getResourceType, resourceType)
                .eq(Integer.valueOf(1).equals(resourceType), QueueBanRecord::getLabId, labId)
                .eq(Integer.valueOf(2).equals(resourceType), QueueBanRecord::getEquipmentId, equipmentId)
                .gt(QueueBanRecord::getEndTime, LocalDateTime.now())
                .orderByDesc(QueueBanRecord::getEndTime)
                .last("limit 1"));
    }

    /** 时段是否已被占用（设备按"全部库存都被占"判定为占满） */
    private boolean slotOccupied(Integer resourceType, Long resourceId, LocalDate date, Long slotId) {
        List<Reservation> occupying = reservationMapper.selectList(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getReserveDate, date)
                .eq(Reservation::getSlotId, slotId)
                .in(Reservation::getStatus, (Object[]) Reservation.OCCUPYING_STATUS));
        if (Integer.valueOf(1).equals(resourceType)) {
            return occupying.stream().anyMatch(item -> Objects.equals(item.getLabId(), resourceId));
        }
        Equipment equipment = equipmentMapper.selectById(resourceId);
        int stock = equipment == null || equipment.getStock() == null ? 0 : equipment.getStock();
        int used = occupying.stream()
                .filter(item -> Objects.equals(item.getEquipmentId(), resourceId))
                .mapToInt(item -> item.getEquipmentCount() == null ? 1 : item.getEquipmentCount())
                .sum();
        if (used >= stock) {
            return true;
        }
        // 附带设备占用也算
        List<Long> ids = occupying.stream().map(Reservation::getId).toList();
        if (ids.isEmpty()) {
            return false;
        }
        int attached = reservationEquipmentMapper.selectList(Wrappers.<com.labreserve.model.entity.ReservationEquipment>lambdaQuery()
                        .in(com.labreserve.model.entity.ReservationEquipment::getReservationId, ids)
                        .eq(com.labreserve.model.entity.ReservationEquipment::getEquipmentId, resourceId))
                .stream().mapToInt(item -> item.getEquipmentCount() == null ? 1 : item.getEquipmentCount()).sum();
        return used + attached >= stock;
    }

    private String resolveResourceNo(Integer resourceType, Long resourceId) {
        if (Integer.valueOf(1).equals(resourceType)) {
            Laboratory lab = laboratoryMapper.selectById(resourceId);
            if (lab == null) {
                throw BizException.notFound("实验室不存在");
            }
            return lab.getLabNo();
        }
        Equipment equipment = equipmentMapper.selectById(resourceId);
        if (equipment == null) {
            throw BizException.notFound("设备不存在");
        }
        return equipment.getResourceNo();
    }

    private Long resolveExperimentTypeId(ResourceQueue queue) {
        if (Integer.valueOf(1).equals(queue.getResourceType())) {
            Laboratory lab = laboratoryMapper.selectById(queue.getLabId());
            return lab == null ? null : lab.getExperimentTypeId();
        }
        Equipment equipment = equipmentMapper.selectById(queue.getEquipmentId());
        if (equipment == null) {
            return null;
        }
        Laboratory lab = laboratoryMapper.selectById(equipment.getLabId());
        return lab == null ? null : lab.getExperimentTypeId();
    }

    private String nextQueueNo(String resourceNo, LocalDate date) {
        String key = RedisKeyConst.queueSeqKey(resourceNo, date);
        long seq = redisUtils.incrementWithTtl(key, java.time.Duration.ofHours(48));
        return "P-" + resourceNo + "-" + YYYYMMDD.format(date) + "-" + String.format("%03d", seq);
    }

    private long positionOf(Integer resourceType, Long resourceId, LocalDate date, Long slotId, Long userId) {
        String queueKey = RedisKeyConst.queueKey(resourceType, resourceId, date, slotId);
        long rank = redisUtils.zRank(queueKey, String.valueOf(userId));
        return rank < 0 ? 0 : rank + 1;
    }

    private String lockKeyOf(ResourceQueue queue) {
        return RedisKeyConst.lockQueueKey(queue.getResourceType(), queue.mainResourceId(),
                queue.getReserveDate(), queue.getSlotId());
    }

    private Long remainSeconds(Long queueId) {
        CandidateNoticeLog pending = noticeLogMapper.selectOne(Wrappers.<CandidateNoticeLog>lambdaQuery()
                .eq(CandidateNoticeLog::getQueueId, queueId)
                .eq(CandidateNoticeLog::getAction, CandidateNoticeLog.ACTION_PENDING)
                .orderByDesc(CandidateNoticeLog::getId)
                .last("limit 1"));
        if (pending == null || pending.getDeadline() == null) {
            return null;
        }
        return java.time.Duration.between(LocalDateTime.now(), pending.getDeadline()).getSeconds();
    }

    private void notifyBehindPositionShift(ResourceQueue queue) {
        String queueKey = RedisKeyConst.queueKey(queue.getResourceType(), queue.mainResourceId(),
                queue.getReserveDate(), queue.getSlotId());
        List<String> members = redisUtils.zRange(queueKey, 0, 4);
        if (CollectionUtils.isEmpty(members)) {
            return;
        }
        String content = "你排队的队伍有变化，当前顺位已前移，可到「我的排队」查看最新顺位。";
        afterCommitUtils.run(() -> members.forEach(member -> {
            try {
                messageService.send(Long.valueOf(member), MessageType.QUEUE_POSITION,
                        "排队顺位更新", content, "QUEUE", queue.getId());
            } catch (Exception e) {
                log.warn("[排队] 顺位通知失败 member={}, {}", member, e.getMessage());
            }
        }));
    }

    private long toEpochMillis(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private List<QueueVO> toVOList(List<ResourceQueue> list) {
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Laboratory> labs = new LinkedHashMap<>();
        List<Long> labIds = list.stream().map(ResourceQueue::getLabId).filter(Objects::nonNull).distinct().toList();
        if (!labIds.isEmpty()) {
            laboratoryMapper.selectBatchIds(labIds).forEach(item -> labs.put(item.getId(), item));
        }
        Map<Long, Equipment> equipments = new LinkedHashMap<>();
        List<Long> equipIds = list.stream().map(ResourceQueue::getEquipmentId)
                .filter(Objects::nonNull).distinct().toList();
        if (!equipIds.isEmpty()) {
            equipmentMapper.selectBatchIds(equipIds).forEach(item -> equipments.put(item.getId(), item));
        }
        Map<Long, TimeSlot> slots = new LinkedHashMap<>();
        List<Long> slotIds = list.stream().map(ResourceQueue::getSlotId).filter(Objects::nonNull).distinct().toList();
        if (!slotIds.isEmpty()) {
            timeSlotMapper.selectBatchIds(slotIds).forEach(item -> slots.put(item.getId(), item));
        }
        Map<Long, User> users = new LinkedHashMap<>();
        List<Long> userIds = list.stream().map(ResourceQueue::getApplicantId).distinct().toList();
        userMapper.selectBatchIds(userIds).forEach(item -> users.put(item.getId(), item));

        return list.stream().map(item -> {
            QueueVO vo = new QueueVO();
            vo.setId(item.getId());
            vo.setQueueNo(item.getQueueNo());
            vo.setResourceType(item.getResourceType());
            vo.setLabId(item.getLabId());
            vo.setEquipmentId(item.getEquipmentId());
            if (Integer.valueOf(1).equals(item.getResourceType())) {
                Laboratory lab = labs.get(item.getLabId());
                vo.setResourceName(lab == null ? null : lab.getLabNo() + " " + lab.getName());
            } else {
                Equipment equipment = equipments.get(item.getEquipmentId());
                vo.setResourceName(equipment == null ? null : equipment.getResourceNo() + " " + equipment.getName());
            }
            vo.setReserveDate(item.getReserveDate());
            vo.setSlotId(item.getSlotId());
            TimeSlot slot = slots.get(item.getSlotId());
            if (slot != null) {
                vo.setSlotName(slot.getName());
                vo.setStartTime(slot.getStartTime());
                vo.setEndTime(slot.getEndTime());
            }
            vo.setApplicantId(item.getApplicantId());
            User user = users.get(item.getApplicantId());
            vo.setApplicantName(user == null ? null : user.getRealName());
            vo.setStatus(item.getStatus());
            vo.setStatusName(statusName(item.getStatus()));
            vo.setJoinTime(item.getJoinTime());
            vo.setCreatedAt(item.getCreatedAt());

            String queueKey = RedisKeyConst.queueKey(item.getResourceType(), item.mainResourceId(),
                    item.getReserveDate(), item.getSlotId());
            long rank = redisUtils.zRank(queueKey, String.valueOf(item.getApplicantId()));
            long total = redisUtils.zCard(queueKey);
            vo.setTotalCount(total);
            if (rank >= 0) {
                vo.setPosition((int) rank + 1);
                vo.setAheadCount((int) rank);
            }
            if (ResourceQueue.NOTIFIED.equals(item.getStatus())) {
                Long remain = remainSeconds(item.getId());
                vo.setRemainSeconds(remain);
                if (remain != null) {
                    vo.setDeadline(LocalDateTime.now().plusSeconds(remain));
                }
            }
            return vo;
        }).toList();
    }

    private Map<Long, String> loadLabNames(List<QueueBanRecord> list) {
        List<Long> ids = list.stream().filter(item -> Integer.valueOf(1).equals(item.getResourceType()))
                .map(QueueBanRecord::getLabId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> map = new LinkedHashMap<>();
        laboratoryMapper.selectBatchIds(ids).forEach(item -> map.put(item.getId(), item.getLabNo() + " " + item.getName()));
        return map;
    }

    private Map<Long, String> loadEquipmentNames(List<QueueBanRecord> list) {
        List<Long> ids = list.stream().filter(item -> Integer.valueOf(2).equals(item.getResourceType()))
                .map(QueueBanRecord::getEquipmentId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> map = new LinkedHashMap<>();
        equipmentMapper.selectBatchIds(ids).forEach(item -> map.put(item.getId(), item.getResourceNo() + " " + item.getName()));
        return map;
    }

    private String statusName(String status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case ResourceQueue.QUEUING -> "排队中";
            case ResourceQueue.NOTIFIED -> "待确认";
            case ResourceQueue.CONFIRMED -> "已确认";
            case ResourceQueue.ABANDONED -> "已放弃";
            case ResourceQueue.TIMEOUT -> "已超时";
            case ResourceQueue.CANCELED -> "已取消";
            default -> status;
        };
    }

    private String actionName(Integer action) {
        if (action == null) {
            return "未响应";
        }
        return switch (action) {
            case CandidateNoticeLog.ACTION_PENDING -> "未响应";
            case CandidateNoticeLog.ACTION_CONFIRMED -> "已确认";
            case CandidateNoticeLog.ACTION_ABANDONED -> "已放弃";
            case CandidateNoticeLog.ACTION_TIMEOUT -> "已超时";
            default -> "未知";
        };
    }

    private String hhmm(java.time.LocalTime time) {
        return time == null ? "" : time.format(HH_MM);
    }
}
