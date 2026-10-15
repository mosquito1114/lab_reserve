package com.labreserve.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 刷新 token 入参（{@code POST /api/auth/refresh}）。
 */
@Data
@Schema(description = "刷新 access token 入参")
public class RefreshTokenDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "登录时返回的 refreshToken")
    @NotBlank(message = "refreshToken 不能为空")
    private String refreshToken;
}
