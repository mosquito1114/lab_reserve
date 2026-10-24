package com.labreserve.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.labreserve.common.constant.RedisKeyConst;
import com.labreserve.common.enums.MessageType;
import com.labreserve.config.QueueProperties;
import com.labreserve.mapper.CandidateNoticeLogMapper;
import com.labreserve.mapper.QueueBanRecordMapper;
import com.labreserve.mapper.ReservationMapper;
import com.labreserve.mapper.ResourceQueueMapper;
import com.labreserve.model.entity.CandidateNoticeLog;
import com.labreserve.model.entity.QueueBanRecord;
import com.labreserve.model.entity.Reservation;
import com.labreserve.model.entity.ResourceQueue;
import com.labreserve.service.MessageService;
import com.labreserve.service.QueueCandidateService;
import com.labreserve.util.AfterCommitUtils;
import com.labreserve.util.RedisLockTemplate;
import com.labreserve.util.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Set;

/**
 * 候补触发实现。
 *
 * <p>关键设计：
 * <ul>
 *   <li><b>先判断时段是否真的空闲</b>：不空闲就什么都不做（避免「有人取消了但时段又被别人抢」时误通知）。</li>
 *   <li><b>队首可能是脏数据</b>（DB 没有对应记录）：清掉 ZSet 里的成员继续看下一位。</li>
 *   <li><b>队首已在确认中</b>（NOTIFIED）：直接返回，保证同一时段只有一个"待确认"。</li>
 *   <li>状态流转全部用 CAS（`UPDATE ... WHERE status = 旧值`），受影响行数为 0 就跳过，
 *       这样与超时任务 / 用户操作并发时不会双写。</li>
 * </ul>
 */
@Slf4j
@Service
public class QueueCandidateServiceImpl implements QueueCandidateService {

    @Autowired
    private ResourceQueueMapper queueMapper;

    @Autowired
    private CandidateNoticeLogMapper noticeLogMapper;

    @Autowired
    private QueueBanRecordMapper queueBanRecordMapper;

    @Autowired
    private ReservationMapper reservationMapper;

    @Autowired
    private RedisLockTemplate redisLockTemplate;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private MessageService messageService;

    @Autowired
    private AfterCommitUtils afterCommitUtils;

    @Autowired
    private QueueProperties queueProperties;

    // ============================================================
    // 顺延
    // ============================================================

    @Override
    public void triggerCandidate(Integer resourceType, Long resourceId, LocalDate reserveDate, Long slotId) {
        if (resourceType == null || resourceId == null || reserveDate == null || slotId == null) {
            return;
        }
        String lockKey = RedisKeyConst.lockQueueKey(resourceType, resourceId, reserveDate, slotId);
        redisLockTemplate.execute(lockKey, () -> {
            if (slotStillOccupied(resourceType, resourceId, reserveDate, slotId)) {
                log.info("[候补] 时段仍被占用，跳过顺延 type={}, id={}, date={}, slot={}",
                        resourceType, resourceId, reserveDate, slotId);
                return;
            }
            String queueKey = RedisKeyConst.queueKey(resourceType, resourceId, reserveDate, slotId);
            // 最多看 50 个成员，防止异常数据导致死循环
            for (int guard = 0; guard < 50; guard++) {
                String userIdText = redisUtils.zFirst(queueKey);
                if (userIdText == null) {
                    return;
                }
                Long userId;
                try {
                    userId = Long.valueOf(userIdText);
                } catch (NumberFormatException e) {
                    redisUtils.zRemove(queueKey, userIdText);
                    continue;
                }
                ResourceQueue head = findQueue(userId, resourceType, resourceId, reserveDate, slotId);
                if (head == null) {
                    redisUtils.zRemove(queueKey, userIdText);
                    continue;
                }
                if (ResourceQueue.NOTIFIED.equals(head.getStatus())) {
                    // 已经有人在确认中，不再通知别人
                    return;
                }
                if (!ResourceQueue.QUEUING.equals(head.getStatus())) {
                    redisUtils.zRemove(queueKey, userIdText);
                    continue;
                }
                int changed = queueMapper.update(null, Wrappers.<ResourceQueue>lambdaUpdate()
                        .eq(ResourceQueue::getId, head.getId())
                        .eq(ResourceQueue::getStatus, ResourceQueue.QUEUING)
                        .set(ResourceQueue::getStatus, ResourceQueue.NOTIFIED));
                if (changed == 0) {
                    // 并发下被别人改了，重新看队首
                    continue;
                }
                notify(head);
                return;
            }
            log.warn("[候补] 顺延尝试超过 50 次，放弃本轮：type={}, id={}, date={}, slot={}",
                    resourceType, resourceId, reserveDate, slotId);
        });
    }

    private void notify(ResourceQueue head) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = now.plusMinutes(queueProperties.getCandidateTimeoutMinutes());
        long seq = noticeLogMapper.selectCount(Wrappers.<CandidateNoticeLog>lambdaQuery()
                .eq(CandidateNoticeLog::getQueueId, head.getId())) + 1;

        CandidateNoticeLog logEntity = new CandidateNoticeLog();
        logEntity.setQueueId(head.getId());
        logEntity.setNoticeTime(now);
        logEntity.setDeadline(deadline);
        logEntity.setChannel(1);
        logEntity.setAction(CandidateNoticeLog.ACTION_PENDING);
        logEntity.setSeq((int) seq);
        noticeLogMapper.insert(logEntity);

        redisUtils.zAdd(RedisKeyConst.QUEUE_NOTICE_DEADLINE, String.valueOf(head.getId()), toEpochMillis(deadline));

        int minutes = queueProperties.getCandidateTimeoutMinutes();
        String content = "你排队的时段已空出，请于 " + minutes + " 分钟内确认（截止 "
                + deadline.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "），"
                + "超时未确认将进入 " + queueProperties.getBanDays() + " 天排队禁入。";
        // 事务提交后再发消息，避免消费者读不到刚落库的通知记录
        afterCommitUtils.run(() -> messageService.send(head.getApplicantId(),
                MessageType.CANDIDATE_NOTICE, "候补通知", content, "QUEUE", head.getId()));
        log.info("[候补] 已通知 queueId={}, userId={}, deadline={}", head.getId(), head.getApplicantId(), deadline);
    }

    // ============================================================
    // 超时扫描
    // ============================================================

    @Override
    public int scanTimeout() {
        LocalDateTime now = LocalDateTime.now();
        Set<ZSetOperations.TypedTuple<String>> due = redisUtils.zRangeByScoreWithScores(
                RedisKeyConst.QUEUE_NOTICE_DEADLINE, 0, toEpochMillis(now), 0, 50);
        if (due == null || due.isEmpty()) {
            return 0;
        }
        int handled = 0;
        for (ZSetOperations.TypedTuple<String> tuple : due) {
            String queueIdText = tuple.getValue();
            if (queueIdText == null) {
                continue;
            }
            Long queueId;
            try {
                queueId = Long.valueOf(queueIdText);
            } catch (NumberFormatException e) {
                redisUtils.zRemove(RedisKeyConst.QUEUE_NOTICE_DEADLINE, queueIdText);
                continue;
            }
            // 通知记录 CAS：0 未响应 → 3 超时（已被确认/放弃的记录不会被改）
            int changed = noticeLogMapper.update(null, Wrappers.<CandidateNoticeLog>lambdaUpdate()
                    .eq(CandidateNoticeLog::getQueueId, queueId)
                    .eq(CandidateNoticeLog::getAction, CandidateNoticeLog.ACTION_PENDING)
                    .le(CandidateNoticeLog::getDeadline, now)
                    .set(CandidateNoticeLog::getAction, CandidateNoticeLog.ACTION_TIMEOUT)
                    .set(CandidateNoticeLog::getActionTime, now));
            if (changed == 0) {
                redisUtils.zRemove(RedisKeyConst.QUEUE_NOTICE_DEADLINE, queueIdText);
                continue;
            }
            ResourceQueue queue = queueMapper.selectById(queueId);
            if (queue == null) {
                redisUtils.zRemove(RedisKeyConst.QUEUE_NOTICE_DEADLINE, queueIdText);
                continue;
            }
            // 排队状态 CAS：NOTIFIED → TIMEOUT
            int queueChanged = queueMapper.update(null, Wrappers.<ResourceQueue>lambdaUpdate()
                    .eq(ResourceQueue::getId, queueId)
                    .eq(ResourceQueue::getStatus, ResourceQueue.NOTIFIED)
                    .set(ResourceQueue::getStatus, ResourceQueue.TIMEOUT));
            if (queueChanged == 0) {
                redisUtils.zRemove(RedisKeyConst.QUEUE_NOTICE_DEADLINE, queueIdText);
                continue;
            }
            redisUtils.zRemove(RedisKeyConst.QUEUE_NOTICE_DEADLINE, queueIdText);
            redisUtils.zRemove(RedisKeyConst.queueKey(queue.getResourceType(), queue.mainResourceId(),
                    queue.getReserveDate(), queue.getSlotId()), String.valueOf(queue.getApplicantId()));

            ban(queue.getApplicantId(), queue, "候补超时未确认");
            afterCommitUtils.run(() -> messageService.send(queue.getApplicantId(),
                    MessageType.CANDIDATE_NOTICE, "候补已超时",
                    "你未在时限内确认候补，已取消本次候补并进入 " + queueProperties.getBanDays()
                            + " 天排队禁入。", "QUEUE", queueId));
            log.warn("[候补] 超时 queueId={}, userId={}", queueId, queue.getApplicantId());

            // 顺延给下一位
            triggerCandidate(queue.getResourceType(), queue.mainResourceId(),
                    queue.getReserveDate(), queue.getSlotId());
            handled++;
        }
        return handled;
    }

    // ============================================================
    // 禁入
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void ban(Long userId, ResourceQueue queue, String reason) {
        if (userId == null || queue == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        QueueBanRecord record = new QueueBanRecord();
        record.setUserId(userId);
        record.setResourceType(queue.getResourceType());
        record.setLabId(queue.getLabId());
        record.setEquipmentId(queue.getEquipmentId());
        record.setReason(reason);
        record.setStartTime(now);
        record.setEndTime(now.plusDays(queueProperties.getBanDays()));
        queueBanRecordMapper.insert(record);
        log.info("[候补] 禁入 userId={}, type={}, lab={}, equip={}, 原因={}, 至 {}",
                userId, queue.getResourceType(), queue.getLabId(), queue.getEquipmentId(),
                reason, record.getEndTime());
    }

    @Override
    public boolean isBanned(Long userId, Integer resourceType, Long labId, Long equipmentId) {
        if (userId == null) {
            return false;
        }
        return queueBanRecordMapper.selectCount(Wrappers.<QueueBanRecord>lambdaQuery()
                .eq(QueueBanRecord::getUserId, userId)
                .eq(QueueBanRecord::getResourceType, resourceType)
                .eq(Integer.valueOf(1).equals(resourceType), QueueBanRecord::getLabId, labId)
                .eq(Integer.valueOf(2).equals(resourceType), QueueBanRecord::getEquipmentId, equipmentId)
                .gt(QueueBanRecord::getEndTime, LocalDateTime.now())) > 0;
    }

    // ============================================================
    // 工具
    // ============================================================

    private ResourceQueue findQueue(Long userId, Integer resourceType, Long resourceId,
                                    LocalDate reserveDate, Long slotId) {
        return queueMapper.selectOne(Wrappers.<ResourceQueue>lambdaQuery()
                .eq(ResourceQueue::getApplicantId, userId)
                .eq(ResourceQueue::getResourceType, resourceType)
                .eq(ResourceQueue::getReserveDate, reserveDate)
                .eq(ResourceQueue::getSlotId, slotId)
                .eq(Integer.valueOf(1).equals(resourceType), ResourceQueue::getLabId, resourceId)
                .eq(Integer.valueOf(2).equals(resourceType), ResourceQueue::getEquipmentId, resourceId)
                .in(ResourceQueue::getStatus, ResourceQueue.QUEUING, ResourceQueue.NOTIFIED)
                .orderByAsc(ResourceQueue::getId)
                .last("limit 1"));
    }

    /** 时段是否仍被占用（实验室：时段独占；设备：库存是否已满） */
    private boolean slotStillOccupied(Integer resourceType, Long resourceId, LocalDate date, Long slotId) {
        if (Integer.valueOf(1).equals(resourceType)) {
            return reservationMapper.selectCount(Wrappers.<Reservation>lambdaQuery()
                    .eq(Reservation::getLabId, resourceId)
                    .eq(Reservation::getReserveDate, date)
                    .eq(Reservation::getSlotId, slotId)
                    .in(Reservation::getStatus, (Object[]) Reservation.OCCUPYING_STATUS)) > 0;
        }
        // 设备：只要还有一条占用中的预约就认为"仍被占用"（库存粒度在入队时已经判断过）
        return reservationMapper.selectCount(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getEquipmentId, resourceId)
                .eq(Reservation::getReserveDate, date)
                .eq(Reservation::getSlotId, slotId)
                .in(Reservation::getStatus, (Object[]) Reservation.OCCUPYING_STATUS)) > 0;
    }

    private long toEpochMillis(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private String text(Object value) {
        return Objects.toString(value, "");
    }
}
