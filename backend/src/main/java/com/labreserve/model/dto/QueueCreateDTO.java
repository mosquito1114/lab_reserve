package com.labreserve.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 加入排队（{@code POST /api/queue/create}）。
 */
@Data
@Schema(description = "加入排队入参")
public class QueueCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "资源类型：1 实验室 / 2 设备", example = "1")
    @NotNull(message = "资源类型不能为空")
    @Min(value = 1, message = "资源类型只能是 1 或 2")
    @Max(value = 2, message = "资源类型只能是 1 或 2")
    private Integer resourceType;

    @Schema(description = "实验室 ID（resourceType=1 必填）")
    private Long labId;

    @Schema(description = "设备 ID（resourceType=2 必填）")
    private Long equipmentId;

    @Schema(description = "预约日期", example = "2026-10-27")
    @NotNull(message = "预约日期不能为空")
    private LocalDate reserveDate;

    @Schema(description = "时段 ID", example = "2")
    @NotNull(message = "时段不能为空")
    private Long slotId;
}
