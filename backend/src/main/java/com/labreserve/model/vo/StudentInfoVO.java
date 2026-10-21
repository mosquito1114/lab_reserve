package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 学生档案（用户详情里的 {@code studentInfo}）。
 *
 * <p>{@code className} / {@code college} / {@code major} / {@code grade} 是只读展示字段，
 * 由 {@code classId} JOIN {@code tb_class} 得到，不落库（{@code design.md} D10）。
 */
@Data
@Schema(description = "学生档案")
public class StudentInfoVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "学号")
    private String studentNo;

    @Schema(description = "班级 ID（可写）")
    private Long classId;

    @Schema(description = "班级名称（只读，来自 tb_class）")
    private String className;

    @Schema(description = "学院（只读）")
    private String college;

    @Schema(description = "专业（只读）")
    private String major;

    @Schema(description = "年级（只读）")
    private Integer grade;
}
