package com.labreserve.job;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.labreserve.common.constant.RedisKeyConst;
import com.labreserve.mapper.ResourceQueueMapper;
import com.labreserve.model.entity.ResourceQueue;
import com.labreserve.util.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 排队 ZSet 与 DB 对账（每天凌晨，迭代 8 的定时任务补全项）。
 *
 * <p>队列顺序只存在 Redis，一旦 Redis 被清空或成员丢失，用户就"看不到自己在排队"。
 * 这里做两件事：
 * <ol>
 *   <li>清掉 ZSet 里已经没有对应 DB 活动记录（QUEUING / NOTIFIED）的成员；</li>
 *   <li>把 DB 里有活动记录、但 ZSet 里缺失的成员按 join_time 补回去。</li>
 * </ol>
 * 只处理 QUEUING / NOTIFIED，终态记录本来就不该在队列里。
 */
@Slf4j
@Component
public class QueueReconcileJob {

    @Autowired
    private ResourceQueueMapper queueMapper;

    @Autowired
    private RedisUtils redisUtils;

    @Scheduled(cron = "0 20 4 * * ?")
    public void reconcile() {
        List<ResourceQueue> active = queueMapper.selectList(Wrappers.<ResourceQueue>lambdaQuery()
                .in(ResourceQueue::getStatus, ResourceQueue.QUEUING, ResourceQueue.NOTIFIED));
        Map<String, Map<String, Double>> expected = new HashMap<>();
        for (ResourceQueue queue : active) {
            String key = RedisKeyConst.queueKey(queue.getResourceType(), queue.mainResourceId(),
                    queue.getReserveDate(), queue.getSlotId());
            double score = queue.getJoinTime() == null ? System.currentTimeMillis()
                    : queue.getJoinTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            expected.computeIfAbsent(key, k -> new HashMap<>())
                    .put(String.valueOf(queue.getApplicantId()), score);
        }

        int removed = 0;
        int added = 0;
        // 1) 清理 Redis 里的脏成员（跳过 deadline ZSet 与序号 key）
        Set<String> keys = redisUtils.keys(RedisKeyConst.QUEUE + "*");
        if (keys != null) {
            for (String key : keys) {
                if (key.startsWith(RedisKeyConst.QUEUE_NOTICE_DEADLINE) || key.contains(":seq:")) {
                    continue;
                }
                Map<String, Double> wanted = expected.getOrDefault(key, Map.of());
                for (String member : redisUtils.zRange(key, 0, -1)) {
                    if (!wanted.containsKey(member)) {
                        redisUtils.zRemove(key, member);
                        removed++;
                    }
                }
            }
        }
        // 2) 补回 DB 有、Redis 缺失的成员
        for (Map.Entry<String, Map<String, Double>> queueEntry : expected.entrySet()) {
            String key = queueEntry.getKey();
            for (Map.Entry<String, Double> member : queueEntry.getValue().entrySet()) {
                if (redisUtils.zScore(key, member.getKey()) == null) {
                    redisUtils.zAdd(key, member.getKey(), member.getValue());
                    added++;
                }
            }
        }
        if (removed > 0 || added > 0) {
            log.info("[排队对账] 清理脏成员 {} 个，补回缺失成员 {} 个（活动队列记录 {} 条）",
                    removed, added, active.size());
        }
    }
}
