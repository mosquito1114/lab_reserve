package com.labreserve.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 维修人员下拉查询（{@code POST /api/user/list_maintainer}，派单用）。
 */
@Data
@Schema(description = "维修人员下拉查询入参")
public class MaintainerQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "维修人员类别：1 校内 / 2 校外；不传返回全部")
    private Integer maintainerType;
}
