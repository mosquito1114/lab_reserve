package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 实验室某日的可用时段。
 */
@Data
@Schema(description = "实验室可用时段")
public class LaboratoryAvailabilityVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long labId;

    private String labNo;

    private String name;

    @Schema(description = "查询日期", example = "2025-09-10")
    private LocalDate date;

    @Schema(description = "该日期是否在开放星期内")
    private Boolean openWeekday;

    @Schema(description = "开放时间范围描述", example = "08:00-18:00")
    private String openTimeText;

    @Schema(description = "各时段可用情况")
    private List<SlotAvailabilityVO> slots;
}
