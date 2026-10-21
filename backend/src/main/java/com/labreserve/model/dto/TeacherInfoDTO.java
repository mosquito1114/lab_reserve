package com.labreserve.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 教师档案入参（新增用户时的 {@code teacherInfo}）。
 */
@Data
@Schema(description = "教师档案入参")
public class TeacherInfoDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "工号", example = "T1005")
    @NotBlank(message = "工号不能为空")
    @Size(max = 20, message = "工号长度不能超过 20")
    private String teacherNo;

    @Schema(description = "所属学院", example = "计算机学院")
    @NotBlank(message = "请选择或填写所属学院")
    @Size(max = 50, message = "学院名称过长")
    private String college;

    @Schema(description = "职称", example = "副教授")
    @Size(max = 30, message = "职称过长")
    private String title;
}
