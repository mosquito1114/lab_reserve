package com.labreserve.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 当前登录用户（存放在 Spring Security 的 SecurityContext 里）。
 *
 * <p>刻意不实现 {@code UserDetails}：
 * 本项目只用它承载「我是谁、我是什么角色」，鉴权逻辑由
 * {@code PermissionService} + {@code @RequirePerm} 负责，
 * 不需要 UserDetails 那套账号状态语义。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long userId;

    private String username;

    private String realName;

    private Long roleId;

    /** 角色编码，见 {@code CommonConst.ROLE_*} */
    private String roleCode;
}
