package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 维修人员下拉项（{@code POST /api/user/list_maintainer}，派单用）。
 */
@Data
@Schema(description = "维修人员下拉项")
public class MaintainerVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String realName;

    private String staffNo;

    @Schema(description = "1 校内 / 2 校外")
    private Integer maintainerType;

    @Schema(description = "校内 / 校外")
    private String maintainerTypeName;
}
