package com.labreserve.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 修改自己的资料（{@code POST /api/user/update_self}）。
 *
 * <p>只能改手机号 / 邮箱 / 邮件通知开关；姓名、角色、账号都不允许自己改。
 * 不存在 {@code id} 参数，从 token 取当前用户——从接口层面就杜绝了「改别人」。
 */
@Data
@Schema(description = "修改自己的资料入参")
public class UserUpdateSelfDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "手机号", example = "13900001234")
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Schema(description = "邮箱")
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱过长")
    private String email;

    @Schema(description = "是否开启邮件通知：0 否 / 1 是")
    @Min(value = 0, message = "只能是 0 或 1")
    @Max(value = 1, message = "只能是 0 或 1")
    private Integer notifyEmail;
}
