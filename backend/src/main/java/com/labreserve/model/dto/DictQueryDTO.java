package com.labreserve.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 字典列表查询入参（实验类型 / 时段共用）。
 *
 * <p>字典数据量小，不做分页；{@code status} 不传表示全部。
 */
@Data
@Schema(description = "字典列表查询入参")
public class DictQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "状态：1 启用 / 0 停用；不传则全部", example = "1")
    private Integer status;
}
