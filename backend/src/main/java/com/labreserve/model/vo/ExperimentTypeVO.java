package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 实验类型（字典）。
 */
@Data
@Schema(description = "实验类型")
public class ExperimentTypeVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    @Schema(description = "编码", example = "VALIDATE")
    private String code;

    @Schema(description = "名称", example = "验证性")
    private String name;

    private Integer sort;

    @Schema(description = "1 启用 / 0 停用")
    private Integer status;
}
