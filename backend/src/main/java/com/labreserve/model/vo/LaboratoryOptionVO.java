package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 实验室下拉项（预约页 / 设备归属选择用）。
 */
@Data
@Schema(description = "实验室下拉项")
public class LaboratoryOptionVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    @Schema(description = "编号", example = "LAB-01")
    private String labNo;

    private String name;

    private Integer capacity;

    private Long experimentTypeId;

    @Schema(description = "1 可用 / 0 停用")
    private Integer status;
}
