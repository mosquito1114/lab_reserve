package com.labreserve.controller;

import com.labreserve.common.constant.CommonConst;
import com.labreserve.common.constant.PermissionConst;
import com.labreserve.common.result.Result;
import com.labreserve.model.vo.SystemHealthVO;
import com.labreserve.model.vo.SystemMonitorVO;
import com.labreserve.security.RequirePerm;
import com.labreserve.util.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
 * 系统接口：健康检查 + 运行状态。
 *
 * <h3>两个接口的职责划分</h3>
 * <ul>
 *   <li>{@code POST /api/system/health}：<b>免登录</b>，只返回 {@code status}（UP / DEGRADED）
 *       与版本信息，供 docker-compose 健康检查与部署验证。不能返回任何运维明细。</li>
 *   <li>{@code POST /api/system/monitor}：需要 {@code system:monitor:view} 权限
 *       （默认只有系统管理员有），返回数据库 / Redis 状态、运行时长、激活配置等运维信息。</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/system")
public class SystemController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private Environment environment;

    @Value("${spring.application.name:lab-reserve}")
    private String applicationName;

    @Value("${lab-reserve.version:unknown}")
    private String version;

    /**
     * {@code POST /api/system/health}（免登录）
     *
     * <p>只暴露「活着 / 降级」，不暴露 DB / Redis 明细与运行时长：
     * <pre>
     * { "code": 200, "message": "success",
     *   "data": { "status": "UP", "application": "lab-reserve", "version": "0.3.0", "serverTime": "…" } }
     * </pre>
     */
    @PostMapping("/health")
    public Result<SystemHealthVO> health() {
        SystemHealthVO vo = new SystemHealthVO();
        vo.setApplication(applicationName);
        vo.setVersion(version);
        vo.setServerTime(now());
        vo.setStatus(overallStatus());
        return Result.ok(vo);
    }

    /**
     * {@code POST /api/system/monitor}（仅 {@code system:monitor:view}，默认系统管理员）
     *
     * <p>返回运维明细：数据库 / Redis 状态、运行时长、激活配置。
     */
    @RequirePerm(PermissionConst.SYSTEM_MONITOR)
    // 不记操作日志：首页每次加载都会拉一次，记日志只会刷屏；这里只做权限控制
    @PostMapping("/monitor")
    public Result<SystemMonitorVO> monitor() {
        SystemMonitorVO vo = new SystemMonitorVO();
        vo.setApplication(applicationName);
        vo.setVersion(version);
        vo.setServerTime(now());
        vo.setProfiles(String.join(",", environment.getActiveProfiles()));
        vo.setUptimeSeconds(ManagementFactory.getRuntimeMXBean().getUptime() / 1000);

        String database = checkDatabase();
        String redis = checkRedis();
        vo.setDatabase(database);
        vo.setRedis(redis);
        vo.setStatus("UP".equals(database) && "UP".equals(redis) ? "UP" : "DEGRADED");
        return Result.ok(vo);
    }

    // ------------------------------------------------------------

    /** 综合状态：数据库与 Redis 都通才算 UP */
    private String overallStatus() {
        boolean dbOk = "UP".equals(checkDatabaseQuietly());
        boolean redisOk = "UP".equals(checkRedisQuietly());
        return dbOk && redisOk ? "UP" : "DEGRADED";
    }

    private String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(CommonConst.DATETIME_PATTERN));
    }

    private String checkDatabaseQuietly() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return "UP";
        } catch (Exception e) {
            return "DOWN";
        }
    }

    private String checkRedisQuietly() {
        try {
            return "PONG".equalsIgnoreCase(redisUtils.ping()) ? "UP" : "DOWN";
        } catch (Exception e) {
            return "DOWN";
        }
    }

    private String checkDatabase() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return "UP";
        } catch (Exception e) {
            log.warn("[运行状态] 数据库不可用: {}", e.getMessage());
            return "DOWN: " + rootMessage(e);
        }
    }

    private String checkRedis() {
        try {
            String pong = redisUtils.ping();
            return "PONG".equalsIgnoreCase(pong) ? "UP" : "DOWN: 响应异常 " + pong;
        } catch (Exception e) {
            log.warn("[运行状态] Redis 不可用: {}", e.getMessage());
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
