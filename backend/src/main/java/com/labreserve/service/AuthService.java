package com.labreserve.service;

import com.labreserve.model.dto.LoginDTO;
import com.labreserve.model.dto.UpdatePasswordDTO;
import com.labreserve.model.vo.CurrentUserVO;
import com.labreserve.model.vo.LoginVO;

/**
 * 认证服务。
 *
 * <p>覆盖需求模块 7 的「用户登录」与前端的会话管理：
 * 登录（含失败锁定）、刷新 access token、登出（token 拉黑）、
 * 取当前用户信息（含权限码与菜单）、修改自己的密码。
 */
public interface AuthService {

    /**
     * 账号密码登录。
     *
     * <p>失败计数存在 Redis（{@code login:fail:{username}}）：
     * 连续失败 5 次锁定 15 分钟；登录成功清零。
     *
     * @return access / refresh token + 用户信息 + 权限码
     */
    LoginVO login(LoginDTO dto);

    /**
     * 用 refresh token 换新的 access token。
     *
     * @return 新的 accessToken（refreshToken 不轮换）
     */
    LoginVO refresh(String refreshToken);

    /**
     * 登出：把当前 access token 的 {@code jti} 写入黑名单，并清除该用户的 refresh token。
     *
     * @param accessToken 当前请求头里的 access token（可能为空，为空则只清本地状态）
     */
    void logout(String accessToken);

    /** 当前登录用户的完整信息（用户 + 权限码 + 菜单） */
    CurrentUserVO getCurrentUser();

    /**
     * 修改自己的密码。
     *
     * <p>改密成功后：当前 access token 立即失效（拉黑）、refresh token 作废、
     * 登录失败计数清零——前端必须重新登录。
     */
    void updatePassword(UpdatePasswordDTO dto, String accessToken);
}
