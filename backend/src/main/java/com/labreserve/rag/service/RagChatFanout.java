package com.labreserve.rag.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * RAG 队列"有位"通知器（design.md §11.1 第 3 项 / §四.0 修订注）。
 *
 * <p>R3 引入公平队列时：
 * <ul>
 *   <li>{@link FairQueueService#drainWaitingQueue()} 当全局信号量有空位，把排在队首的 userId 调到这里</li>
 *   <li>本类推一条通知到该用户的 SSE / 收件箱 / 长连接，前端拿后再重试 send</li>
 * </ul>
 *
 * <p>本 R1 提交里仍是<b>空实现</b>：仅记一条 INFO 日志，便于 R3 接入时定位。
 * 不影响 R1 摄取主链。
 *
 * @author lab_reserve
 */
@Slf4j
@Component
public class RagChatFanout {

    /**
     * 通知指定用户"AI 队列有位可重试"。
     *
     * @param userId 等待队列的 userId
     */
    public void notify(Long userId) {
        if (userId == null) {
            return;
        }
        log.info("[RAG] (R3 占位) 通知用户 {} 排队完成, 可重试 send", userId);
        // R3 实装:
        // 1) 写一条收件箱消息到 Redis (key: lab:reserve:rag:fanout:user:{userId})
        //    (前端 GET /api/rag/chat/poll 拉取)
        // 或
        // 2) 走 WebSocket 推一帧
    }
}
