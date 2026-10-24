package com.labreserve.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 统计查询公共入参（{@code api.md} §9.1）。
 *
 * <p>需求原文的「2025 年 9 月份计算机学院验证性实验」就是 beginDate/endDate + college + experimentTypeId 这组参数。
 */
@Data
@Schema(description = "统计查询入参")
public class StatisticsQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "开始日期（默认本月初）", example = "2026-10-01")
    private LocalDate beginDate;

    @Schema(description = "结束日期（默认今天）", example = "2026-10-31")
    private LocalDate endDate;

    @Schema(description = "申请人学院（教师按教师档案、学生按班级学院）", example = "计算机学院")
    private String college;

    @Schema(description = "实验类型 ID")
    private Long experimentTypeId;

    @Schema(description = "实验室 ID")
    private Long labId;

    @Schema(description = "趋势分组：day / week / month（仅 get_reservation_trend 使用）", example = "day")
    private String groupBy;

    @Schema(description = "导出类型：reservation / occupancy / experiment-type / success-rate / resource-ratio")
    private String type;
}
