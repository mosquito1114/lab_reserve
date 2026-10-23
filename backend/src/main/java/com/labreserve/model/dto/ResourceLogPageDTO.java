package com.labreserve.model.dto;

import com.labreserve.common.result.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 资源更新日志分页查询（{@code POST /api/resource_log/get_page}）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "资源更新日志分页查询入参")
public class ResourceLogPageDTO extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "资源类型：1 实验室 / 2 设备")
    private Integer resourceType;

    @Schema(description = "资源 ID")
    private Long resourceId;

    @Schema(description = "操作：1 新增 / 2 修改 / 3 状态变更")
    private Integer operation;

    @Schema(description = "操作人 ID")
    private Long operatorId;

    @Schema(description = "开始时间", example = "2025-09-01 00:00:00")
    private LocalDateTime beginTime;

    @Schema(description = "结束时间", example = "2025-09-30 23:59:59")
    private LocalDateTime endTime;
}
