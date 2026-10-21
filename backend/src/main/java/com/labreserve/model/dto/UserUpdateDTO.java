package com.labreserve.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 修改用户（{@code POST /api/user/update}，仅系统管理员）。
 *
 * <p>刻意不包含 {@code roleCode} 与 {@code username}：角色不允许修改
 * （换角色等于换身份，历史数据引用会错位；确需变更请注销后重建），
 * 登录账号同理不可改。学号 / 工号也不可改（教务业务主键）。
 */
@Data
@Schema(description = "修改用户入参")
public class UserUpdateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "用户 ID")
    @NotNull(message = "用户 ID 不能为空")
    private Long id;

    @Schema(description = "真实姓名")
    @Size(max = 50, message = "姓名过长")
    private String realName;

    @Schema(description = "手机号", example = "13800001234")
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Schema(description = "邮箱")
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱过长")
    private String email;

    @Schema(description = "是否开启邮件通知：0 否 / 1 是")
    private Integer notifyEmail;

    @Valid
    @Schema(description = "学生档案（只允许改班级；学号不可改）")
    private StudentInfoUpdateDTO studentInfo;

    @Valid
    @Schema(description = "教师档案（可改学院 / 职称；工号不可改）")
    private TeacherInfoDTO teacherInfo;

    @Valid
    @Schema(description = "管理类人员档案（可改部门 / 校内校外；工号不可改）")
    private StaffInfoDTO staffInfo;
}
