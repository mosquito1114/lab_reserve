package com.labreserve.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户详情入参（{@code POST /api/user/get_info}）。
 *
 * <p>比通用 {@link IdDTO} 多一个 {@code revealPhone}：是否请求完整手机号。
 */
@Data
@Schema(description = "用户详情入参")
public class UserInfoDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "用户 ID")
    @NotNull(message = "用户 ID 不能为空")
    private Long id;

    /**
     * 是否返回完整手机号。
     *
     * <p>需要 {@code user:view-phone} 权限；本人查看自己时不受限制且不写日志。
     */
    @Schema(description = "是否返回完整手机号（需 user:view-phone 权限）")
    private Boolean revealPhone = Boolean.FALSE;
}
