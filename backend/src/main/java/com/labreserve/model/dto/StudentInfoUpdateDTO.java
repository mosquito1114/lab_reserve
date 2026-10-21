package com.labreserve.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 修改学生档案（{@code POST /api/user/update} 里的 {@code studentInfo}）。
 *
 * <p>只能改班级：学号是教务业务主键，不允许在系统里改。
 */
@Data
@Schema(description = "修改学生档案入参")
public class StudentInfoUpdateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "班级 ID", example = "2")
    @NotNull(message = "请选择班级")
    private Long classId;
}
