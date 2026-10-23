package com.labreserve.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 设备状态变更（{@code POST /api/equipment/update_status}）。
 */
@Data
@Schema(description = "设备状态变更入参")
public class EquipmentStatusDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "设备 ID 不能为空")
    private Long id;

    @Schema(description = "1 正常 / 2 维修中 / 3 报废 / 0 停用", example = "2")
    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态取值 0/1/2/3")
    @Max(value = 3, message = "状态取值 0/1/2/3")
    private Integer status;

    @Schema(description = "更新原因（必填）", example = "主板故障送修")
    @NotBlank(message = "更新原因不能为空")
    @Size(max = 255, message = "更新原因不能超过 255 个字符")
    private String updateReason;
}
