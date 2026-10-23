package com.labreserve.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 实验类型新增 / 修改入参（{@code POST /api/experiment_type/create|update}）。
 *
 * <p>{@code create} 时不要传 {@code id}；{@code update} 必须传。
 */
@Data
@Schema(description = "实验类型保存入参")
public class ExperimentTypeSaveDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键 ID（修改时必填）")
    private Long id;

    @Schema(description = "编码，全局唯一", example = "VALIDATE")
    @NotBlank(message = "编码不能为空")
    @Size(max = 30, message = "编码不能超过 30 个字符")
    private String code;

    @Schema(description = "名称", example = "验证性")
    @NotBlank(message = "名称不能为空")
    @Size(max = 50, message = "名称不能超过 50 个字符")
    private String name;

    @Schema(description = "排序号，小的在前", example = "2")
    private Integer sort;

    @Schema(description = "状态：1 启用 / 0 停用", example = "1")
    private Integer status;
}
