package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 登录响应（{@code login} 与 {@code refresh} 的 data）。
 */
@Data
@Schema(description = "登录响应")
public class LoginVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "访问令牌，放入请求头 Authorization: Bearer <token>")
    private String accessToken;

    @Schema(description = "刷新令牌，仅用于 /api/auth/refresh")
    private String refreshToken;

    @Schema(description = "accessToken 有效期（秒）")
    private Long expiresIn;

    private LoginUserVO user;

    @Schema(description = "权限码集合，前端据此控制按钮与菜单")
    private List<String> permissions;
}
