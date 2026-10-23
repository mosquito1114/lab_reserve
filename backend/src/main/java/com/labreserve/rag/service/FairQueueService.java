package com.labreserve.rag.service;

import com.labreserve.common.constant.RedisKeyConst;
import com.labreserve.common.exception.BizException;
import com.labreserve.rag.config.RagProperties;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Step 0 · FairQueue（限频 + 全局并发）。
 *
 * <p>两道闸门（rag-design §12.4）：
 * <ol>
 *   <li><b>每用户限频</b>：Redisson {@link RRateLimiter}，{@code chat.rate-limit-per-user-per-min}
 *       次/分钟。超限抛 {@link BizException}(429)。</li>
 *   <li><b>全局并发</b>：本机 Semaphore（{@code chat.global-concurrency}）。
 *       R3 单实例部署，用 JVM Semaphore 即可；多实例部署时换 Redisson Semaphore。</li>
 * </ol>
 *
 * <p>配额释放在 {@code RagChatServiceImpl.doFinally}，异常路径也保证释放。
 *
 * <p><b>为什么不做角色优先级队列</b>：演示规模下单实例 + 全局并发 16 足够，
 * 优先级队列（{@code RScoredSortedSet}）放 R5，需要时再加（见 rag-design §十一）。
 */
@Slf4j
@Service
public class FairQueueService {

    @Autowired
    private RedissonClient redisson;

    @Autowired
    private RagProperties props;

    /** 全局并发信号量（惰性初始化，避免启动期 Redisson 未就绪） */
    private volatile java.util.concurrent.Semaphore globalSemaphore;

    private java.util.concurrent.Semaphore semaphore() {
        if (globalSemaphore == null) {
            synchronized (this) {
                if (globalSemaphore == null) {
                    int permits = Math.max(1, props.getChat().getGlobalConcurrency());
                    globalSemaphore = new java.util.concurrent.Semaphore(permits);
                    log.info("[RAG][QUEUE] 全局并发信号量初始化: permits={}", permits);
                }
            }
        }
        return globalSemaphore;
    }

    /**
     * 检查配额（限频 + 并发）。任一不满足直接抛异常。
     *
     * @param userId 当前用户
     */
    public void checkQuota(Long userId) {
        BizException.requireFound(userId, "未登录");
        checkRateLimit(userId);
        acquireSemaphore();
    }

    /** 释放并发许可（在流的 doFinally 调） */
    public void releaseQuota(Long userId) {
        java.util.concurrent.Semaphore sem = globalSemaphore;
        if (sem != null && sem.availablePermits() < props.getChat().getGlobalConcurrency()) {
            sem.release();
        }
    }

    // ------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------

    /**
     * 每用户限频：{@code rate-limit-per-user-per-min} 次/分钟。
     */
    private void checkRateLimit(Long userId) {
        int limit = Math.max(1, props.getChat().getRateLimitPerUserPerMin());
        RRateLimiter limiter = redisson.getRateLimiter(RedisKeyConst.ragRateLimitKey(userId));
        // 首次使用时设置速率；已设置则沿用（Redisson 内部按 key+rateType 缓存）
        limiter.trySetRate(RateType.OVERALL, limit, 1, RateIntervalUnit.MINUTES);

        if (!limiter.tryAcquire(1)) {
            log.warn("[RAG][QUEUE] 用户 {} 触发限频 ({}次/分钟)", userId, limit);
            throw new BizException(com.labreserve.common.result.ResultCode.RAG_RATE_LIMIT,
                    "AI 请求过于频繁，请稍后再试");
        }
    }

    /**
     * 全局并发：拿不到许可就短暂等待（{@code queue-wait-timeout-ms}），仍拿不到则 429。
     */
    private void acquireSemaphore() {
        long timeoutMs = props.getChat().getQueueWaitTimeoutMs();
        try {
            if (!semaphore().tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
                log.warn("[RAG][QUEUE] 全局并发已满,等待 {}ms 后仍拿不到许可", timeoutMs);
                throw new BizException(com.labreserve.common.result.ResultCode.RAG_RATE_LIMIT,
                        "AI 正在繁忙，请稍后重试");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(500, "等待并发许可被中断");
        }
    }
}