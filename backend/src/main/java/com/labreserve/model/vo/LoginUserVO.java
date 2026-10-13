package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 登录用户信息（登录响应 / {@code get_info} 里的 {@code user}）。
 *
 * <p>{@code phone} 已经过 {@code @Sensitive} 脱敏：本人查看自己时返回完整号码，
 * 其他人看到 {@code 138****1234}（见 {@code design.md} §7.6）。
 */
@Data
@Schema(description = "登录用户信息")
public class LoginUserVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String username;

    private String realName;

    /** STUDENT / TEACHER / SYS_ADMIN / LAB_ADMIN / MAINTAINER */
    private String roleCode;

    private String roleName;

    /** 手机号（已脱敏） */
    private String phone;

    private String email;

    /** 是否开启邮件通知：0 否 / 1 是 */
    private Integer notifyEmail;

    /** 头像地址（迭代 0 预留字段） */
    private String avatar;
}
