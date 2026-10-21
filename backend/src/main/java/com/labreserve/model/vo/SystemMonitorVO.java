package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 系统运行状态（{@code POST /api/system/monitor}，需要 {@code system:monitor:view}）。
 *
 * <p>比公开的 {@link SystemHealthVO} 多了 DB / Redis 明细、激活配置与运行时长，
 * 属于运维信息，<b>只给系统管理员</b>。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "系统运行状态（仅系统管理员）")
public class SystemMonitorVO extends SystemHealthVO {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "激活的 Spring profile", example = "dev")
    private String profiles;

    @Schema(description = "数据库：UP 或 DOWN: 原因", example = "UP")
    private String database;

    @Schema(description = "Redis：UP 或 DOWN: 原因", example = "UP")
    private String redis;

    @Schema(description = "运行时长（秒）")
    private Long uptimeSeconds;
}
