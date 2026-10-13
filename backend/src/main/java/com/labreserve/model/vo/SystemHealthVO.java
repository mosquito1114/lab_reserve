package com.labreserve.model.vo;

import lombok.Data;

/**
 * 系统健康检查结果。
 *
 * <p>用于容器健康检查与部署验证：
 * 只有 {@code status = UP} 才算真正可用（数据库与 Redis 都连得上）。
 */
@Data
public class SystemHealthVO {

    /** UP / DEGRADED */
    private String status;

    private String application;

    private String version;

    /** 激活的 Spring profile */
    private String profiles;

    /** 服务器当前时间（yyyy-MM-dd HH:mm:ss） */
    private String serverTime;

    /** 数据库连通性：UP 或 DOWN: 原因 */
    private String database;

    /** Redis 连通性：UP 或 DOWN: 原因 */
    private String redis;

    /** 应用已运行秒数 */
    private long uptimeSeconds;
}
