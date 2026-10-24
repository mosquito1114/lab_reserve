package com.labreserve.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 预约附带设备（实验室顺带借的设备，或调整申请里增减的设备）。
 */
@Data
@Schema(description = "预约附带设备")
public class ReservationEquipmentDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "设备 ID", example = "10")
    @NotNull(message = "设备 ID 不能为空")
    private Long equipmentId;

    @Schema(description = "数量", example = "2")
    @NotNull(message = "设备数量不能为空")
    @Min(value = 1, message = "设备数量至少为 1")
    private Integer equipmentCount;
}
