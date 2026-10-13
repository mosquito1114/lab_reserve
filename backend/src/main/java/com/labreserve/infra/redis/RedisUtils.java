package com.labreserve.infra.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
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
 * Redis 常用操作封装。
 *
 * <p>本项目的 Redis Key 分两类，序列化方式不同，因此这里同时持有两个模板：
 * <ul>
 *   <li>{@link StringRedisTemplate}：计数器、ZSet member、Stream —— 用纯字符串，方便 redis-cli 直接看</li>
 *   <li>{@link RedisTemplate}：需要存对象时使用（JSON 序列化）</li>
 * </ul>
 *
 * <p>Key 一律从 {@link com.labreserve.common.constant.RedisKeyConst} 拼接，禁止硬编码。
 */
@Component
@RequiredArgsConstructor
public class RedisUtils {

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    public StringRedisTemplate str() {
        return stringRedisTemplate;
    }

    public RedisTemplate<String, Object> obj() {
        return redisTemplate;
    }

    // ============================================================
    // String
    // ============================================================

    public void set(String key, String value) {
        stringRedisTemplate.opsForValue().set(key, value);
    }

    public void set(String key, String value, Duration ttl) {
        stringRedisTemplate.opsForValue().set(key, value, ttl);
    }

    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public void set(String key, Object value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    public String get(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    public Object getObject(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public Boolean delete(String key) {
        return stringRedisTemplate.delete(key);
    }

    public Long delete(Collection<String> keys) {
        return stringRedisTemplate.delete(keys);
    }

    public Boolean hasKey(String key) {
        return stringRedisTemplate.hasKey(key);
    }

    /** 设置过期时间；{@code timeout <= 0} 表示不过期 */
    public Boolean expire(String key, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return Boolean.TRUE;
        }
        return stringRedisTemplate.expire(key, ttl);
    }

    public Long getExpire(String key) {
        return stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
    }

    // ============================================================
    // 计数器
    // ============================================================

    /** 自增 1，返回自增后的值 */
    public long increment(String key) {
        Long value = stringRedisTemplate.opsForValue().increment(key);
        return value == null ? 0L : value;
    }

    public long increment(String key, long delta) {
        Long value = stringRedisTemplate.opsForValue().increment(key, delta);
        return value == null ? 0L : value;
    }

    /** 自增 1 并设置过期时间（用于「每日重置」的序号） */
    public long incrementWithTtl(String key, Duration ttl) {
        Long value = stringRedisTemplate.opsForValue().increment(key);
        if (value != null && value == 1L) {
            stringRedisTemplate.expire(key, ttl);
        }
        return value == null ? 0L : value;
    }

    // ============================================================
    // ZSet（排队队列 / 收件箱 / 候补 deadline）
    // ============================================================

    /**
     * 加入 ZSet，仅当 member 不存在时（NX）。
     *
     * <p>排队入队依赖这个 NX 语义保证「同一用户同一资源同一时段只入队一次」的幂等性。
     *
     * @return true 表示本次真的插入了
     */
    public boolean zAddIfAbsent(String key, String member, double score) {
        Boolean added = stringRedisTemplate.opsForZSet().addIfAbsent(key, member, score);
        return Boolean.TRUE.equals(added);
    }

    public boolean zAdd(String key, String member, double score) {
        Boolean added = stringRedisTemplate.opsForZSet().add(key, member, score);
        return Boolean.TRUE.equals(added);
    }

    public Long zRemove(String key, String... members) {
        return stringRedisTemplate.opsForZSet().remove(key, (Object[]) members);
    }

    /** 顺位 = 下标 + 1；不在队列中返回 -1 */
    public long zRank(String key, String member) {
        Long rank = stringRedisTemplate.opsForZSet().rank(key, member);
        return rank == null ? -1L : rank;
    }

    public long zCard(String key) {
        Long size = stringRedisTemplate.opsForZSet().zCard(key);
        return size == null ? 0L : size;
    }

    public Double zScore(String key, String member) {
        return stringRedisTemplate.opsForZSet().score(key, member);
    }

    /** 按 score 升序取区间 [start, end]（含端点） */
    public Set<String> zRange(String key, long start, long end) {
        Set<String> set = stringRedisTemplate.opsForZSet().range(key, start, end);
        return set == null ? Collections.emptySet() : set;
    }

    /** 按 score 升序分页取 */
    public List<String> zPage(String key, long pageNum, long pageSize) {
        long start = Math.max(0, (pageNum - 1) * pageSize);
        long end = start + pageSize - 1;
        Set<String> set = stringRedisTemplate.opsForZSet().range(key, start, end);
        return set == null ? Collections.emptyList() : List.copyOf(set);
    }

    /** 逆序分页取（最新的在前） */
    public List<String> zPageDesc(String key, long pageNum, long pageSize) {
        long start = Math.max(0, (pageNum - 1) * pageSize);
        long end = start + pageSize - 1;
        Set<String> set = stringRedisTemplate.opsForZSet().reverseRange(key, start, end);
        return set == null ? Collections.emptyList() : List.copyOf(set);
    }

    /** 按 score 区间取 member（score 从 -inf 到 max） */
    public Set<String> zRangeByScore(String key, double min, double max, long offset, long count) {
        Set<String> set = stringRedisTemplate.opsForZSet()
                .rangeByScore(key, min, max, offset, count);
        return set == null ? Collections.emptySet() : set;
    }

    public ZSetOperations.TypedTuple<String> zPopMin(String key) {
        return stringRedisTemplate.opsForZSet().popMin(key);
    }

    // ============================================================
    // 通用
    // ============================================================

    /** 按前缀扫描 key（数据量大时慎用，只用于运维对账） */
    public Set<String> keys(String pattern) {
        return stringRedisTemplate.keys(pattern);
    }

    /** 连通性探测 */
    public String ping() {
        // 显式指定 RedisCallback，否则 execute 在 RedisCallback / SessionCallback 之间重载歧义
        return stringRedisTemplate.execute((RedisCallback<String>) connection -> connection.ping());
    }
}
