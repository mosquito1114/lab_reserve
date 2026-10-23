package com.labreserve.model.dto;

import com.labreserve.common.result.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 班级分页查询（{@code POST /api/class/get_page}，管理员维护用）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "班级分页查询入参")
public class ClassPageDTO extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "关键字：班级名 / 专业模糊匹配", example = "软工")
    private String keyword;

    @Schema(description = "学院", example = "计算机学院")
    private String college;

    @Schema(description = "年级", example = "2021")
    private Integer grade;

    @Schema(description = "1 正常 / 0 停用")
    private Integer status;
}
