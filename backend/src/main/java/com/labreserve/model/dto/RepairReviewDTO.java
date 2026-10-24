package com.labreserve.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 复核工单（{@code POST /api/repair_order/review}）。
 *
 * <p>复核通过时可以顺手把设备状态改成「维修中」（{@code equipmentStatus=2}）。
 */
@Data
@Schema(description = "复核工单入参")
public class RepairReviewDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "工单 ID 不能为空")
    private Long id;

    @Schema(description = "复核结果：CONFIRMED 确认故障 / REJECTED 驳回", example = "CONFIRMED")
    @NotBlank(message = "复核结果不能为空")
    @Pattern(regexp = "^(CONFIRMED|REJECTED)$", message = "复核结果只能是 CONFIRMED 或 REJECTED")
    private String result;

    @Schema(description = "优先级：1 低 / 2 中 / 3 高 / 4 紧急（确认时可选）", example = "3")
    @Min(value = 1, message = "优先级 1-4")
    @Max(value = 4, message = "优先级 1-4")
    private Integer priority;

    @Schema(description = "同时把设备状态改成：2 维修中（可选）", example = "2")
    private Integer equipmentStatus;

    @Schema(description = "复核意见（驳回必填）", example = "确认设备故障，已转维修")
    @Size(max = 255, message = "复核意见不能超过 255 个字符")
    private String opinion;
}
