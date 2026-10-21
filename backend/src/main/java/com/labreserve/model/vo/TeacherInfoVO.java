package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 教师档案（用户详情里的 {@code teacherInfo}）。
 */
@Data
@Schema(description = "教师档案")
public class TeacherInfoVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "工号")
    private String teacherNo;

    @Schema(description = "所属学院")
    private String college;

    @Schema(description = "职称")
    private String title;
}
