package com.labreserve.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 登录入参（{@code POST /api/auth/login}）。
 */
@Data
@Schema(description = "登录入参")
public class LoginDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "登录账号（学号 / 工号 / 管理员账号）", example = "admin")
    @NotBlank(message = "请输入账号")
    @Size(max = 50, message = "账号长度不能超过 50")
    private String username;

    @Schema(description = "密码", example = "123456")
    @NotBlank(message = "请输入密码")
    @Size(min = 6, max = 64, message = "密码长度需在 6~64 位之间")
    private String password;
}
