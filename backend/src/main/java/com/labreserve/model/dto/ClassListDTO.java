package com.labreserve.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 班级下拉查询（{@code POST /api/class/get_list}）。
 *
 * <p>教师预约实验室时要选「上课班级」，这个接口就是给那个下拉用的，
 * 只返回启用状态的班级。
 */
@Data
@Schema(description = "班级下拉查询入参")
public class ClassListDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "学院筛选", example = "计算机学院")
    private String college;

    @Schema(description = "年级筛选", example = "2021")
    private Integer grade;

    @Schema(description = "班级名 / 专业模糊匹配", example = "软工")
    private String keyword;
}
