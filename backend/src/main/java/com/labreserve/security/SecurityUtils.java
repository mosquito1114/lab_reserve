package com.labreserve.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录用户读取工具。
 *
 * <p>未登录（或匿名）时所有方法都返回 {@code null}，调用方需自行判空。
 * 操作日志切面会在未登录场景下把操作人记为 null（例如登录失败）。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /** 获取当前登录用户；未登录返回 null */
    public static LoginUser getLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        return (principal instanceof LoginUser loginUser) ? loginUser : null;
    }

    /** 当前用户 ID；未登录返回 null */
    public static Long getUserId() {
        LoginUser user = getLoginUser();
        return user == null ? null : user.getUserId();
    }

    /** 当前用户名；未登录返回 null */
    public static String getUsername() {
        LoginUser user = getLoginUser();
        return user == null ? null : user.getUsername();
    }

    /** 当前用户真实姓名；未登录返回 null */
    public static String getRealName() {
        LoginUser user = getLoginUser();
        return user == null ? null : user.getRealName();
    }

    /** 当前用户角色编码；未登录返回 null */
    public static String getRoleCode() {
        LoginUser user = getLoginUser();
        return user == null ? null : user.getRoleCode();
    }

    /** 当前用户角色 ID；未登录返回 null */
    public static Long getRoleId() {
        LoginUser user = getLoginUser();
        return user == null ? null : user.getRoleId();
    }

    public static boolean isLogin() {
        return getLoginUser() != null;
    }

    /** 判断当前用户是否属于指定角色 */
    public static boolean hasRole(String roleCode) {
        String current = getRoleCode();
        return current != null && current.equals(roleCode);
    }
}
