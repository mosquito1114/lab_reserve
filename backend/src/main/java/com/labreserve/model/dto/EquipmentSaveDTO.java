package com.labreserve.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 设备新增 / 修改入参。
 */
@Data
@Schema(description = "设备保存入参")
public class EquipmentSaveDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键 ID（修改时必填）")
    private Long id;

    @Schema(description = "资源编号，格式 ZY-XX-YYY", example = "ZY-01-003")
    @NotBlank(message = "资源编号不能为空")
    @Pattern(regexp = "^ZY-\\d{2}-\\d{3}$", message = "资源编号格式应为 ZY-两位数字-三位数字，如 ZY-01-003")
    private String resourceNo;

    @Schema(description = "名称", example = "微型计算机")
    @NotBlank(message = "名称不能为空")
    @Size(max = 50, message = "名称不能超过 50 个字符")
    private String name;

    @Schema(description = "型号", example = "联想 M720q")
    @Size(max = 50, message = "型号不能超过 50 个字符")
    private String model;

    @Schema(description = "库存数量", example = "5")
    @NotNull(message = "库存数量不能为空")
    @Min(value = 1, message = "库存数量至少为 1")
    private Integer stock;

    @Schema(description = "所属实验室 ID", example = "1")
    @NotNull(message = "所属实验室不能为空")
    private Long labId;

    @Schema(description = "下次校准日期", example = "2026-03-01")
    private LocalDate nextCalibrationDate;

    @Schema(description = "备注")
    @Size(max = 255, message = "备注不能超过 255 个字符")
    private String remark;

    @Schema(description = "更新原因（必填）", example = "新增设备")
    @NotBlank(message = "更新原因不能为空")
    @Size(max = 255, message = "更新原因不能超过 255 个字符")
    private String updateReason;
}
