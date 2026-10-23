package com.labreserve.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 实验室下拉查询（{@code POST /api/laboratory/list_option}）。
 */
@Data
@Schema(description = "实验室下拉查询入参")
public class LaboratoryOptionQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "状态：1 可用 / 0 停用；不传默认只返回可用", example = "1")
    private Integer status;

    @Schema(description = "实验类型 ID（可选，预约时按类型过滤）")
    private Long experimentTypeId;
}
