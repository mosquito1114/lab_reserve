package com.labreserve.service;

import com.labreserve.model.dto.RolePermissionUpdateDTO;
import com.labreserve.model.vo.PermissionGroupVO;
import com.labreserve.model.vo.RolePermissionVO;
import com.labreserve.model.vo.RoleVO;

import java.util.List;

/**
 * 角色与权限管理服务（需求模块 7）。
 *
 * <p>精简 RBAC（{@code design.md} K2 方案 C）：角色固定 5 个、用户单角色，
 * 因此界面能改的只有「角色拥有哪些权限」，本服务即围绕这一点：
 * 列出角色 → 读某角色已勾选的权限 → 全量覆盖保存（并让 Redis 缓存立即失效）。
 */
public interface RoleService {

    /** 角色列表（含权限数、用户数），供角色权限页左侧展示 */
    List<RoleVO> listRoles();

    /** 某角色当前已勾选的权限 */
    RolePermissionVO getRolePermissions(Long roleId);

    /**
     * 保存角色权限（全量覆盖）。
     *
     * <p>约束：不允许把 {@code system:role:update} 从 {@code SYS_ADMIN} 上摘掉（防止把自己锁死）。
     */
    void updateRolePermissions(RolePermissionUpdateDTO dto);

    /** 权限点分组列表，供权限勾选树渲染 */
    List<PermissionGroupVO> listPermissionGroups();
}
