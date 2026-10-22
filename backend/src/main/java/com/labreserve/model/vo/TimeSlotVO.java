package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalTime;

/**
 * 固定时段（字典）。
 */
@Data
@Schema(description = "固定时段")
public class TimeSlotVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    @Schema(description = "编码", example = "S2")
    private String code;

    @Schema(description = "名称", example = "3-4节")
    private String name;

    @Schema(description = "开始时间", example = "10:00")
    private LocalTime startTime;

    @Schema(description = "结束时间", example = "11:40")
    private LocalTime endTime;

    private Integer sort;

    @Schema(description = "1 启用 / 0 停用")
    private Integer status;
}
