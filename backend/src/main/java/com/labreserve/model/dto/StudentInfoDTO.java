package com.labreserve.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 学生档案入参（新增用户时的 {@code studentInfo}）。
 *
 * <p>只提交学号与班级：学院 / 专业 / 班级名由 {@code classId} 决定，
 * 不接受前端传入（避免双真相源）。
 */
@Data
@Schema(description = "学生档案入参")
public class StudentInfoDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "学号", example = "2021002")
    @NotBlank(message = "学号不能为空")
    @Size(max = 20, message = "学号长度不能超过 20")
    private String studentNo;

    @Schema(description = "班级 ID", example = "1")
    @NotNull(message = "请选择班级")
    private Long classId;
}
