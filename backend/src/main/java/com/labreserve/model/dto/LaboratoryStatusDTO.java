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
 * 实验室停用 / 启用（{@code POST /api/laboratory/update_status}）。
 */
@Data
@Schema(description = "实验室状态变更入参")
public class LaboratoryStatusDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "实验室 ID 不能为空")
    private Long id;

    @Schema(description = "1 可用 / 0 停用", example = "0")
    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态只能是 0 或 1")
    @Max(value = 1, message = "状态只能是 0 或 1")
    private Integer status;

    @Schema(description = "更新原因（必填）", example = "实验室装修停用")
    @NotBlank(message = "更新原因不能为空")
    @Size(max = 255, message = "更新原因不能超过 255 个字符")
    private String updateReason;
}
