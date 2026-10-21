package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 班级（{@code POST /api/class/get_list|get_page} 的返回项）。
 */
@Data
@Schema(description = "班级")
public class ClassVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    @Schema(description = "班级名称", example = "软工2101")
    private String className;

    private String college;

    private String major;

    private Integer grade;

    @Schema(description = "班级人数（教务口径）")
    private Integer studentCount;

    /** 1 正常 / 0 停用 */
    private Integer status;
}
