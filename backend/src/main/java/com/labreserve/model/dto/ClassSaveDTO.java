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
 * 新增 / 修改班级（{@code POST /api/class/create|update}）。
 *
 * <p>{@code className} 全局唯一；班级是学生学院/专业的唯一来源（{@code design.md} D10）。
 */
@Data
@Schema(description = "新增/修改班级入参")
public class ClassSaveDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "班级 ID（修改时必填，新增时为空）")
    private Long id;

    @Schema(description = "班级名称", example = "软工2103")
    @NotBlank(message = "班级名称不能为空")
    @Size(max = 50, message = "班级名称过长")
    private String className;

    @Schema(description = "所属学院", example = "计算机学院")
    @NotBlank(message = "请选择所属学院")
    @Size(max = 50, message = "学院名称过长")
    private String college;

    @Schema(description = "专业", example = "软件工程")
    @NotBlank(message = "请填写专业")
    @Size(max = 50, message = "专业名称过长")
    private String major;

    @Schema(description = "年级", example = "2021")
    @NotNull(message = "请填写年级")
    @Min(value = 1990, message = "年级不合法")
    @Max(value = 2100, message = "年级不合法")
    private Integer grade;

    @Schema(description = "班级人数（教务口径）", example = "43")
    @Min(value = 0, message = "班级人数不能为负")
    private Integer studentCount;

    @Schema(description = "1 正常 / 0 停用")
    @Min(value = 0, message = "状态只能是 0 或 1")
    @Max(value = 1, message = "状态只能是 0 或 1")
    private Integer status;
}
