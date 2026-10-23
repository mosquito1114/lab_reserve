package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 设备（列表项）。
 */
@Data
@Schema(description = "设备")
public class EquipmentVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    @Schema(description = "资源编号", example = "ZY-01-003")
    private String resourceNo;

    private String name;

    private String model;

    private Integer stock;

    private Long labId;

    @Schema(description = "所属实验室编号", example = "LAB-01")
    private String labNo;

    @Schema(description = "所属实验室名称")
    private String labName;

    @Schema(description = "1 正常 / 2 维修中 / 3 报废 / 0 停用")
    private Integer status;

    @Schema(description = "状态中文名", example = "正常")
    private String statusName;

    @Schema(description = "下次校准日期")
    private LocalDate nextCalibrationDate;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
