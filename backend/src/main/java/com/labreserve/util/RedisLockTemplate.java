package com.labreserve.util;

import com.labreserve.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Redis 分布式锁。
 *
 * <p>对应 {@code agents/design.md} §6「需分布式锁 / 原子操作的接口清单」。
 * <b>所有需要并发控制的接口都必须通过这里加锁</b>，禁止在业务代码里散落
 * {@code setIfAbsent} + {@code try/finally}。
 *
 * <h3>用法</h3>
 * <pre>{@code
 * redisLockTemplate.execute(lockKey, 3, 10, () -> {
 *     // 临界区：判冲 + 落库
 *     return reservation;
 * });
 * }</pre>
 *
 * <h3>实现要点</h3>
 * <ul>
 *   <li>加锁：{@code SET key value NX PX leaseMillis}，value 为 UUID，保证只有持有者能释放</li>
 *   <li>解锁：Lua 脚本「比对 value 再删除」，保证判断与删除的原子性</li>
 *   <li>获取失败时按固定间隔自旋重试，直到超出 waitSeconds 才抛 409</li>
 *   <li><b>不做看门狗续期</b>：本项目临界区都是「判冲 + 单表写入」的短操作，
 *       租约 10 秒足够；真出现超时说明有慢 SQL，应该去查 SQL 而不是加续期</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisLockTemplate {

    private final StringRedisTemplate stringRedisTemplate;

    /** 解锁脚本：只有 value 匹配才删除，防止误删他人的锁 */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    /** 自旋间隔（毫秒） */
    private static final long SPIN_INTERVAL_MS = 50L;

    // ------------------------------------------------------------
    // 对外 API
    // ------------------------------------------------------------

    /**
     * 加锁执行有返回值的临界区。
     *
     * @param key          锁 Key（从 RedisKeyConst 拼接）
     * @param waitSeconds  最长等待秒数，超时抛 409
     * @param leaseSeconds 租约秒数，到期自动释放
     * @param supplier     临界区逻辑
     * @throws BizException 获取锁超时
     */
    public <T> T execute(String key, long waitSeconds, long leaseSeconds, Supplier<T> supplier) {
        String token = UUID.randomUUID().toString();
        if (!tryLock(key, token, waitSeconds, leaseSeconds)) {
            log.warn("[分布式锁] 获取超时，key={}, wait={}s", key, waitSeconds);
            throw BizException.conflict("系统繁忙，请稍后重试");
        }
        try {
            return supplier.get();
        } finally {
            unlock(key, token);
        }
    }

    /**
     * 加锁执行无返回值的临界区。
     */
    public void execute(String key, long waitSeconds, long leaseSeconds, Runnable action) {
        execute(key, waitSeconds, leaseSeconds, () -> {
            action.run();
            return null;
        });
    }

    /**
     * 尝试加锁（不阻塞等待，只试一次）。
     *
     * @return true 表示加锁成功
     */
    public boolean tryLock(String key, String token, long leaseSeconds) {
        Boolean ok = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, token, Duration.ofSeconds(leaseSeconds));
        return Boolean.TRUE.equals(ok);
    }

    /**
     * 自旋等待加锁。
     *
     * @param waitSeconds 最长等待秒数
     */
    public boolean tryLock(String key, String token, long waitSeconds, long leaseSeconds) {
        long deadline = System.currentTimeMillis() + waitSeconds * 1000L;
        do {
            if (tryLock(key, token, leaseSeconds)) {
                return true;
            }
            try {
                Thread.sleep(SPIN_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        } while (System.currentTimeMillis() < deadline);
        return false;
    }

    /**
     * 释放锁（Lua 原子比对 + 删除）。
     */
    public boolean unlock(String key, String token) {
        try {
            Long result = stringRedisTemplate.execute(
                    UNLOCK_SCRIPT, Collections.singletonList(key), token);
            return result != null && result > 0;
        } catch (Exception e) {
            log.warn("[分布式锁] 释放失败，key={}（租约会自动到期）", key, e);
            return false;
        }
    }
}
