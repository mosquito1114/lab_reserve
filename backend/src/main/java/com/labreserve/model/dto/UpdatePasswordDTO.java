package com.labreserve.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 修改自己的密码（{@code POST /api/auth/update_password}）。
 *
 * <p>改密成功后当前 access token 会被立即拉黑，前端必须重新登录。
 */
@Data
@Schema(description = "修改密码入参")
public class UpdatePasswordDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "原密码")
    @NotBlank(message = "请输入原密码")
    private String oldPassword;

    @Schema(description = "新密码（6~64 位）")
    @NotBlank(message = "请输入新密码")
    @Size(min = 6, max = 64, message = "新密码长度需在 6~64 位之间")
    private String newPassword;
}
