package com.labreserve.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 新增用户（{@code POST /api/user/create}，仅系统管理员）。
 *
 * <p>按角色提交对应的扩展块之一：
 * <ul>
 *   <li>{@code STUDENT} → {@code studentInfo}</li>
 *   <li>{@code TEACHER} → {@code teacherInfo}</li>
 *   <li>{@code SYS_ADMIN} / {@code LAB_ADMIN} / {@code MAINTAINER} → {@code staffInfo}</li>
 * </ul>
 * 服务端会校验「角色与扩展块是否匹配」。
 *
 * <p>注意：DTO 里<b>没有也不接受</b>任何身份证号字段（安全红线）。
 */
@Data
@Schema(description = "新增用户入参")
public class UserCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "登录账号（学号 / 工号 / 管理员账号）", example = "2021002")
    @NotBlank(message = "账号不能为空")
    @Size(max = 50, message = "账号长度不能超过 50")
    private String username;

    @Schema(description = "初始密码（6~64 位，服务端 BCrypt 存储）", example = "123456")
    @NotBlank(message = "初始密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度需在 6~64 位之间")
    private String password;

    @Schema(description = "真实姓名", example = "李四")
    @NotBlank(message = "姓名不能为空")
    @Size(max = 50, message = "姓名过长")
    private String realName;

    @Schema(description = "角色编码", example = "STUDENT")
    @NotBlank(message = "请选择角色")
    private String roleCode;

    @Schema(description = "手机号（明文存储，接口层脱敏）", example = "13800001234")
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Schema(description = "邮箱", example = "lisi@x.com")
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱过长")
    private String email;

    @Schema(description = "是否开启邮件通知：0 否 / 1 是")
    private Integer notifyEmail;

    @Valid
    @Schema(description = "学生档案（roleCode=STUDENT 必填）")
    private StudentInfoDTO studentInfo;

    @Valid
    @Schema(description = "教师档案（roleCode=TEACHER 必填）")
    private TeacherInfoDTO teacherInfo;

    @Valid
    @Schema(description = "管理类人员档案（三个管理类角色必填）")
    private StaffInfoDTO staffInfo;
}
