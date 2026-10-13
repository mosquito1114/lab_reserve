package com.labreserve.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Redisson 配置。
 *
 * <p>Redisson 在本项目里<b>只负责分布式锁</b>（{@code util/RedisLockTemplate}）：
 * <ul>
 *   <li>可重入（同一线程可重复加锁）</li>
 *   <li>看门狗自动续期（不传 leaseTime 时，业务没跑完锁不会提前过期）</li>
 *   <li>Lua 脚本原子解锁，只有持有者能释放</li>
 * </ul>
 *
 * <p>常规读写（计数器 / ZSet 排队 / 收件箱 / Redis Stream）一律用 Spring Boot 自带的
 * {@code StringRedisTemplate}，不引入 Redisson 的对象 API，
 * 这样在 redis-cli 里能直接看懂数据。
 *
 * <p>这里复用 {@code spring.data.redis.*} 的配置，不额外定义环境变量。
 * 之所以用普通 {@code redisson} 而不是 {@code redisson-spring-boot-starter}，
 * 是为了避开 starter 对 {@code redisson-spring-data-XX} 的版本绑定。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedissonConfig {

    private final RedisProperties redisProperties;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        String address = "redis://%s:%d".formatted(
                redisProperties.getHost(), redisProperties.getPort());

        Config config = new Config();
        SingleServerConfig single = config.useSingleServer()
                .setAddress(address)
                .setDatabase(redisProperties.getDatabase())
                // 连接池
                .setConnectionMinimumIdleSize(4)
                .setConnectionPoolSize(16)
                .setIdleConnectionTimeout(10_000)
                .setConnectTimeout(5_000)
                // 命令超时与重试
                .setTimeout(3_000)
                .setRetryAttempts(3)
                .setRetryInterval(1_500);

        if (StringUtils.hasText(redisProperties.getPassword())) {
            single.setPassword(redisProperties.getPassword());
        }

        RedissonClient client = Redisson.create(config);
        log.info("[Redisson] 初始化完成，address={}, database={}", address, redisProperties.getDatabase());
        return client;
    }
}
