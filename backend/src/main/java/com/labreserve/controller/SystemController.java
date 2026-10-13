package com.labreserve.controller;

import com.labreserve.common.constant.CommonConst;
import com.labreserve.common.result.R;
import com.labreserve.infra.redis.RedisUtils;
import com.labreserve.vo.SystemHealthVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 系统辅助接口。
 *
 * <p>只有健康检查一个接口，供 docker-compose 的健康检查与部署验证使用。
 * <b>不需要登录</b>，因此不能返回任何敏感信息。
 */
@Slf4j
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemController {

    private final JdbcTemplate jdbcTemplate;
    private final RedisUtils redisUtils;
    private final Environment environment;

    @Value("${spring.application.name:lab-reserve}")
    private String applicationName;

    @Value("${lab-reserve.version:unknown}")
    private String version;

    /**
     * {@code POST /api/system/health}
     *
     * <p>示例响应：
     * <pre>
     * { "code": 200, "message": "success",
     *   "data": { "status": "UP", "database": "UP", "redis": "UP", ... } }
     * </pre>
     */
    @PostMapping("/health")
    public R<SystemHealthVO> health() {
        SystemHealthVO vo = new SystemHealthVO();
        vo.setApplication(applicationName);
        vo.setVersion(version);
        vo.setProfiles(String.join(",", environment.getActiveProfiles()));
        vo.setServerTime(LocalDateTime.now().format(
                DateTimeFormatter.ofPattern(CommonConst.DATETIME_PATTERN)));
        vo.setUptimeSeconds(ManagementFactory.getRuntimeMXBean().getUptime() / 1000);

        String database = checkDatabase();
        String redis = checkRedis();
        vo.setDatabase(database);
        vo.setRedis(redis);
        vo.setStatus("UP".equals(database) && "UP".equals(redis) ? "UP" : "DEGRADED");

        return R.ok(vo);
    }

    // ------------------------------------------------------------

    private String checkDatabase() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return "UP";
        } catch (Exception e) {
            log.warn("[健康检查] 数据库不可用: {}", e.getMessage());
            return "DOWN: " + rootMessage(e);
        }
    }

    private String checkRedis() {
        try {
            String pong = redisUtils.ping();
            return "PONG".equalsIgnoreCase(pong) ? "UP" : "DOWN: 响应异常 " + pong;
        } catch (Exception e) {
            log.warn("[健康检查] Redis 不可用: {}", e.getMessage());
            return "DOWN: " + rootMessage(e);
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable current = e;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null) {
            return current.getClass().getSimpleName();
        }
        return Arrays.stream(message.split("\n"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .limit(1)
                .collect(Collectors.joining());
    }
}
