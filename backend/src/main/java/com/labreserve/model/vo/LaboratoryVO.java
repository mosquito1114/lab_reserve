package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 实验室（列表项）。
 */
@Data
@Schema(description = "实验室")
public class LaboratoryVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    @Schema(description = "编号", example = "LAB-01")
    private String labNo;

    private String name;

    private String location;

    @Schema(description = "开放开始时间", example = "08:00")
    private LocalTime openTimeStart;

    @Schema(description = "开放结束时间", example = "18:00")
    private LocalTime openTimeEnd;

    @Schema(description = "开放星期（原始值）", example = "1,2,3,4,5")
    private String openWeekdays;

    @Schema(description = "开放星期（中文展示）", example = "周一至周五")
    private String openWeekdaysText;

    private Integer capacity;

    private Long experimentTypeId;

    @Schema(description = "实验类型名称", example = "验证性")
    private String experimentTypeName;

    @Schema(description = "1 可用 / 0 停用")
    private Integer status;

    private String description;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
