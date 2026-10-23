package com.labreserve.model.dto;

import com.labreserve.common.result.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 设备分页查询（{@code POST /api/equipment/get_page}）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "设备分页查询入参")
public class EquipmentPageDTO extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "资源编号模糊匹配", example = "ZY-01")
    private String resourceNo;

    @Schema(description = "名称 / 型号 模糊匹配", example = "微型计算机")
    private String keyword;

    @Schema(description = "所属实验室 ID")
    private Long labId;

    @Schema(description = "型号模糊匹配")
    private String model;

    @Schema(description = "状态：1 正常 / 2 维修中 / 3 报废 / 0 停用")
    private Integer status;
}
