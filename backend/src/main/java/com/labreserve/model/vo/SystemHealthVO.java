package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 健康检查结果（{@code POST /api/system/health}，<b>免登录</b>）。
 *
 * <p>只暴露「活着 / 降级」与版本信息，供 docker-compose 健康检查与部署验证使用；
 * DB / Redis 的明细、运行时长等运维信息在 {@link SystemMonitorVO} 里，
 * 需要 {@code system:monitor:view} 权限才能看。
 */
@Data
@Schema(description = "健康检查结果（公开）")
public class SystemHealthVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "UP：数据库与 Redis 都通；DEGRADED：有依赖不可用", example = "UP")
    private String status;

    private String application;

    private String version;

    @Schema(description = "服务器时间", example = "2026-10-21 22:00:00")
    private String serverTime;
}
