package com.labreserve.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 我的实验课表查询（学生端）。
 */
@Data
@Schema(description = "我的实验课表查询入参")
public class ReservationTimetableDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "开始日期（不传默认本学期起点 = 今天-30 天）", example = "2026-10-01")
    private LocalDate beginDate;

    @Schema(description = "结束日期（不传默认今天+30 天）", example = "2026-10-31")
    private LocalDate endDate;

    @Schema(description = "实验室 ID（可选）")
    private Long labId;
}
