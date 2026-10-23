package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 设备下拉项。
 */
@Data
@Schema(description = "设备下拉项")
public class EquipmentOptionVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    @Schema(description = "资源编号", example = "ZY-01-003")
    private String resourceNo;

    private String name;

    private String model;

    private Integer stock;

    private Long labId;

    @Schema(description = "1 正常 / 2 维修中 / 3 报废 / 0 停用")
    private Integer status;
}
