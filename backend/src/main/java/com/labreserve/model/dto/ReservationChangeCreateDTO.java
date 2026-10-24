package com.labreserve.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 发起调整申请（{@code POST /api/reservation_change/create}）。
 *
 * <p>{@code changeType=1}（修改）需要新的日期 / 时段；{@code changeType=2}（取消）不需要。
 */
@Data
@Schema(description = "发起调整申请入参")
public class ReservationChangeCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "预约 ID 不能为空")
    private Long reservationId;

    @Schema(description = "调整类型：1 修改 / 2 取消", example = "1")
    @NotNull(message = "调整类型不能为空")
    @Min(value = 1, message = "调整类型只能是 1 或 2")
    @Max(value = 2, message = "调整类型只能是 1 或 2")
    private Integer changeType;

    @Schema(description = "申请原因", example = "课程调整，需换到下午")
    @NotBlank(message = "申请原因不能为空")
    @Size(max = 255, message = "申请原因不能超过 255 个字符")
    private String reason;

    @Schema(description = "新的预约日期（changeType=1 必填）", example = "2026-10-26")
    private LocalDate newReserveDate;

    @Schema(description = "新的时段 ID（changeType=1 必填）", example = "4")
    private Long newSlotId;

    @Schema(description = "新的附带设备清单（可选，为空表示不变）")
    @Valid
    private List<ReservationEquipmentDTO> equipments;
}
