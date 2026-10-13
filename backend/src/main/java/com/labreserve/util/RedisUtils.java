package com.labreserve.util;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 操作封装。
 *
 * <p>统一使用 {@link StringRedisTemplate}：本项目存在 Redis 里的东西
 * （计数器、ZSet 队列、收件箱索引、Stream、序号）都是字符串或数字，
 * 用纯字符串序列化在 {@code redis-cli} 里能直接看懂，
 * 也避免 JSON 序列化带来的引号与转义问题。
 *
 * <p>需要存对象时，由调用方自己用 {@code JsonUtils} 转成 JSON 字符串再存。
 *
 * <p>分布式锁不在本类里，见 {@link RedisLockTemplate}。
 *
 * <p>Key 一律从 {@link com.labreserve.common.constant.RedisKeyConst} 拼接，禁止硬编码。
 *
 * <p><b>TTL 提醒</b>：排队 ZSet、收件箱 ZSet、候补 deadline ZSet 都是业务数据而非缓存，
 * 设计上不设过期，因此 Redis 的 {@code maxmemory-policy} 必须是 {@code noeviction}。
 */
@Component
@RequiredArgsConstructor
public class RedisUtils {

    private final StringRedisTemplate redis;

    /** 需要原生 API 时直接用（例如 Redis Stream 的 XADD / XREADGROUP） */
    public StringRedisTemplate template() {
        return redis;
    }

    // ============================================================
    // String
    // ============================================================

    public void set(String key, String value) {
        redis.opsForValue().set(key, value);
    }

    public void set(String key, String value, Duration ttl) {
        redis.opsForValue().set(key, value, ttl);
    }

    public String get(String key) {
        return redis.opsForValue().get(key);
    }

    public Boolean delete(String key) {
        return redis.delete(key);
    }

    public Long delete(Collection<String> keys) {
        return redis.delete(keys);
    }

    public Boolean hasKey(String key) {
        return redis.hasKey(key);
    }

    /** 设置过期时间；ttl 为 null / 0 / 负数时不做任何事（表示永不过期） */
    public Boolean expire(String key, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return Boolean.TRUE;
        }
        return redis.expire(key, ttl);
    }

    /** 剩余过期秒数，-1 表示永不过期，-2 表示 key 不存在 */
    public Long getExpire(String key) {
        return redis.getExpire(key, TimeUnit.SECONDS);
    }

    // ============================================================
    // 计数器
    // ============================================================

    /** 自增 1，返回自增后的值 */
    public long increment(String key) {
        Long value = redis.opsForValue().increment(key);
        return value == null ? 0L : value;
    }

    public long increment(String key, long delta) {
        Long value = redis.opsForValue().increment(key, delta);
        return value == null ? 0L : value;
    }

    /**
     * 自增 1 并在第一次自增时设置过期时间。
     *
     * <p>用于「每日重置」的序号（预约号、排队号），避免 key 无限堆积。
     */
    public long incrementWithTtl(String key, Duration ttl) {
        Long value = redis.opsForValue().increment(key);
        if (value != null && value == 1L) {
            redis.expire(key, ttl);
        }
        return value == null ? 0L : value;
    }

    // ============================================================
    // ZSet（排队队列 / 候补截止时间 / 收件箱）
    // ============================================================

    /**
     * 加入 ZSet，仅当 member 不存在时（NX）。
     *
     * <p>排队入队依赖这个 NX 语义保证「同一用户同一资源同一时段只入队一次」的幂等性。
     *
     * @return true 表示本次真的插入了
     */
    public boolean zAddIfAbsent(String key, String member, double score) {
        Boolean added = redis.opsForZSet().addIfAbsent(key, member, score);
        return Boolean.TRUE.equals(added);
    }

    public boolean zAdd(String key, String member, double score) {
        Boolean added = redis.opsForZSet().add(key, member, score);
        return Boolean.TRUE.equals(added);
    }

    public Long zRemove(String key, String... members) {
        return redis.opsForZSet().remove(key, (Object[]) members);
    }

    /** 顺位 = 下标 + 1；不在队列中返回 -1 */
    public long zRank(String key, String member) {
        Long rank = redis.opsForZSet().rank(key, member);
        return rank == null ? -1L : rank;
    }

    public long zCard(String key) {
        Long size = redis.opsForZSet().zCard(key);
        return size == null ? 0L : size;
    }

    public Double zScore(String key, String member) {
        return redis.opsForZSet().score(key, member);
    }

    /** 按 score 升序取区间 [start, end]（含端点），只取 member */
    public List<String> zRange(String key, long start, long end) {
        Set<String> set = redis.opsForZSet().range(key, start, end);
        return set == null ? Collections.emptyList() : List.copyOf(set);
    }

    /** 取队首（顺位第一的 member），队列为空返回 null */
    public String zFirst(String key) {
        Set<String> set = redis.opsForZSet().range(key, 0, 0);
        return (set == null || set.isEmpty()) ? null : set.iterator().next();
    }

    /** 按 score 升序分页取 member */
    public List<String> zPage(String key, long pageNum, long pageSize) {
        long start = Math.max(0, (pageNum - 1) * pageSize);
        return zRange(key, start, start + pageSize - 1);
    }

    /** 逆序分页取 member（最新的在前） */
    public List<String> zPageDesc(String key, long pageNum, long pageSize) {
        long start = Math.max(0, (pageNum - 1) * pageSize);
        long end = start + pageSize - 1;
        Set<String> set = redis.opsForZSet().reverseRange(key, start, end);
        return set == null ? Collections.emptyList() : List.copyOf(set);
    }

    /** 按 score 区间取 member（含 score 的元组，用于候补超时扫描） */
    public Set<ZSetOperations.TypedTuple<String>> zRangeByScoreWithScores(
            String key, double min, double max, long offset, long count) {
        Set<ZSetOperations.TypedTuple<String>> set = redis.opsForZSet()
                .rangeByScoreWithScores(key, min, max, offset, count);
        return set == null ? Collections.emptySet() : set;
    }

    // ============================================================
    // 通用
    // ============================================================

    /** 按前缀扫描 key（生产慎用，只用于运维对账与定时任务） */
    public Set<String> keys(String pattern) {
        return redis.keys(pattern);
    }

    /** 连通性探测 */
    public String ping() {
        // 显式指定 RedisCallback，否则 execute 在 RedisCallback / SessionCallback 之间重载歧义
        return redis.execute((RedisCallback<String>) connection -> connection.ping());
    }
}
