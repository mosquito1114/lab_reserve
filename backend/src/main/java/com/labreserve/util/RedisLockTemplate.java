package com.labreserve.util;

import com.labreserve.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 分布式锁。
 *
 * <p>对应 {@code agents/design.md} §6「需分布式锁 / 原子操作的接口清单」。
 * <b>所有需要并发控制的接口都必须通过这里加锁</b>，禁止在业务代码里自己写
 * {@code tryLock} + {@code try/finally}。
 *
 * <p>底层用 Redisson 的 {@link RLock}，相比手写 SETNX + Lua 多了三件事：
 * 可重入、看门狗自动续期、解锁的原子性。
 *
 * <h3>用法</h3>
 * <pre>{@code
 * // 用默认值（等待 3s，租约 10s）
 * redisLockTemplate.execute(lockKey, () -> {
 *     // 临界区：判冲 + 落库
 *     return reservation;
 * });
 *
 * // 业务可能跑得久，交给看门狗续期（不设租约）
 * redisLockTemplate.execute(lockKey, 3, 0, () -> { ... });
 * }</pre>
 *
 * <h3>租约怎么选</h3>
 * <ul>
 *   <li>{@code leaseSeconds > 0}：固定租约，到点自动释放，<b>不看门狗</b>。
 *       适合「判冲 + 单表写入」这类毫秒级短临界区。</li>
 *   <li>{@code leaseSeconds <= 0}：走 Redisson 看门狗，默认 30s 一次续期，
 *       业务没结束锁不会掉。适合临界区耗时不确定的场景。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisLockTemplate {

    private final RedissonClient redissonClient;

    /** 默认最长等待时间（秒） */
    public static final long DEFAULT_WAIT_SECONDS = 3L;

    /** 默认租约（秒） */
    public static final long DEFAULT_LEASE_SECONDS = 10L;

    /** 走看门狗续期（不设固定租约） */
    public static final long LEASE_WATCHDOG = 0L;

    // ------------------------------------------------------------
    // 对外 API
    // ------------------------------------------------------------

    /** 加锁执行有返回值的临界区（默认等待 3s，租约 10s） */
    public <T> T execute(String key, Supplier<T> supplier) {
        return execute(key, DEFAULT_WAIT_SECONDS, DEFAULT_LEASE_SECONDS, supplier);
    }

    /** 加锁执行无返回值的临界区（默认等待 3s，租约 10s） */
    public void execute(String key, Runnable action) {
        execute(key, () -> {
            action.run();
            return null;
        });
    }

    /** 加锁执行无返回值的临界区，自定义等待与租约 */
    public void execute(String key, long waitSeconds, long leaseSeconds, Runnable action) {
        execute(key, waitSeconds, leaseSeconds, () -> {
            action.run();
            return null;
        });
    }

    /**
     * 加锁执行有返回值的临界区。
     *
     * @param key          锁 Key（从 {@code RedisKeyConst} 拼接）
     * @param waitSeconds  最长等待秒数，超时抛 409
     * @param leaseSeconds 租约秒数；{@code <= 0} 表示交给看门狗自动续期
     * @param supplier     临界区逻辑
     * @throws BizException 获取锁超时或被中断
     */
    public <T> T execute(String key, long waitSeconds, long leaseSeconds, Supplier<T> supplier) {
        RLock lock = redissonClient.getLock(key);
        boolean acquired = false;
        try {
            acquired = tryLock(lock, waitSeconds, leaseSeconds);
            if (!acquired) {
                log.warn("[分布式锁] 获取超时，key={}, wait={}s", key, waitSeconds);
                throw BizException.conflict("系统繁忙，请稍后重试");
            }
            return supplier.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(500, "获取分布式锁被中断，请重试");
        } finally {
            unlockQuietly(lock, acquired);
        }
    }

    /**
     * 尝试加锁，不抛异常。
     *
     * @return true 表示拿到了锁
     */
    public boolean tryLock(RLock lock, long waitSeconds, long leaseSeconds) throws InterruptedException {
        if (leaseSeconds > 0) {
            return lock.tryLock(waitSeconds, leaseSeconds, TimeUnit.SECONDS);
        }
        // 不设租约 → Redisson 看门狗负责续期
        return lock.tryLock(waitSeconds, TimeUnit.SECONDS);
    }

    // ------------------------------------------------------------

    /**
     * 释放锁。
     *
     * <p>必须判断 {@code isHeldByCurrentThread()}：如果临界区太长导致租约先到期，
     * 直接 unlock 会抛 {@code IllegalMonitorStateException}，反而掩盖了真正的业务异常。
     */
    private void unlockQuietly(RLock lock, boolean acquired) {
        if (!acquired) {
            return;
        }
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (Exception e) {
            log.warn("[分布式锁] 释放失败，key={}（锁会自动到期）", lock.getName(), e);
        }
    }
}
