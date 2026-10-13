package com.labreserve.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.labreserve.common.constant.CommonConst;
import com.labreserve.common.constant.PermissionConst;
import com.labreserve.common.exception.BizException;
import com.labreserve.mapper.PermissionMapper;
import com.labreserve.mapper.RoleMapper;
import com.labreserve.mapper.RolePermissionMapper;
import com.labreserve.mapper.UserMapper;
import com.labreserve.model.dto.RolePermissionUpdateDTO;
import com.labreserve.model.entity.Permission;
import com.labreserve.model.entity.Role;
import com.labreserve.model.entity.RolePermission;
import com.labreserve.model.vo.PermissionGroupVO;
import com.labreserve.model.vo.PermissionItemVO;
import com.labreserve.model.vo.RolePermissionVO;
import com.labreserve.model.vo.RoleVO;
import com.labreserve.security.PermissionService;
import com.labreserve.service.RoleService;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 角色与权限管理实现。
 *
 * <h3>保存权限的两条硬约束</h3>
 * <ol>
 *   <li>权限 ID 必须真实存在（防止前端传脏数据写出悬空关联）</li>
 *   <li>不允许从 {@code SYS_ADMIN} 摘掉 {@code system:role:update}——
 *       否则一旦保存，系统管理员就再也进不了「角色权限」页，属于把自己锁死</li>
 * </ol>
 *
 * <p>保存成功后立即删除该角色的权限缓存（{@code lab:reserve:perm:role:{roleId}}），
 * 使权限变更对下一次请求即刻生效。
 */
@Slf4j
@Service
public class RoleServiceImpl implements RoleService {

    @Autowired
    private RoleMapper roleMapper;
    @Autowired
    private PermissionMapper permissionMapper;
    @Autowired
    private RolePermissionMapper rolePermissionMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private PermissionService permissionService;

    @Override
    public List<RoleVO> listRoles() {
        List<Role> roles = roleMapper.selectList(
                Wrappers.<Role>lambdaQuery().orderByAsc(Role::getId));
        Map<Long, Long> permissionCounts = toCountMap(rolePermissionMapper.countGroupByRole());
        Map<Long, Long> userCounts = toCountMap(userMapper.countGroupByRole());

        List<RoleVO> result = new ArrayList<>(roles.size());
        for (Role role : roles) {
            RoleVO vo = new RoleVO();
            vo.setId(role.getId());
            vo.setCode(role.getCode());
            vo.setName(role.getName());
            vo.setDescription(role.getDescription());
            vo.setPermissionCount(permissionCounts.getOrDefault(role.getId(), 0L));
            vo.setUserCount(userCounts.getOrDefault(role.getId(), 0L));
            result.add(vo);
        }
        return result;
    }

    @Override
    public RolePermissionVO getRolePermissions(Long roleId) {
        Role role = requireRole(roleId);
        List<Long> permissionIds = rolePermissionMapper.selectList(
                        Wrappers.<RolePermission>lambdaQuery().eq(RolePermission::getRoleId, roleId))
                .stream()
                .map(RolePermission::getPermissionId)
                .toList();

        List<String> codes = permissionIds.isEmpty()
                ? List.of()
                : permissionMapper.selectBatchIds(permissionIds).stream()
                        .map(Permission::getCode)
                        .toList();

        RolePermissionVO vo = new RolePermissionVO();
        vo.setRoleId(role.getId());
        vo.setRoleCode(role.getCode());
        vo.setRoleName(role.getName());
        vo.setPermissionIds(permissionIds);
        vo.setPermissions(codes);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRolePermissions(RolePermissionUpdateDTO dto) {
        Role role = requireRole(dto.getRoleId());
        List<Long> permissionIds = dto.getPermissionIds() == null
                ? List.of()
                : dto.getPermissionIds().stream().filter(Objects::nonNull).distinct().toList();

        // ① 权限点存在性校验
        List<Permission> permissions = permissionIds.isEmpty()
                ? List.of()
                : permissionMapper.selectBatchIds(permissionIds);
        if (permissions.size() != permissionIds.size()) {
            throw BizException.badRequest("存在无效的权限点，请刷新页面后重试");
        }

        // ② 防锁死：系统管理员必须保留「修改角色权限」这个权限
        if (CommonConst.ROLE_SYS_ADMIN.equals(role.getCode())
                && permissions.stream().noneMatch(p -> PermissionConst.CRITICAL_PERMISSION.equals(p.getCode()))) {
            throw BizException.badRequest("不能移除系统管理员的「" + PermissionConst.SYSTEM_ROLE_UPDATE + "」权限，否则将无法再修改角色权限");
        }

        // ③ 全量覆盖：先删后插（同一事务）
        rolePermissionMapper.delete(Wrappers.<RolePermission>lambdaQuery().eq(RolePermission::getRoleId, role.getId()));
        for (Permission permission : permissions) {
            RolePermission relation = new RolePermission();
            relation.setRoleId(role.getId());
            relation.setPermissionId(permission.getId());
            rolePermissionMapper.insert(relation);
        }

        // ④ 缓存失效：下一次请求自动回源
        permissionService.evictRole(role.getId());
        log.info("[权限] 角色 {}（{}）的权限已更新，共 {} 项", role.getName(), role.getCode(), permissions.size());
    }

    @Override
    public List<PermissionGroupVO> listPermissionGroups() {
        List<Permission> permissions = permissionMapper.selectList(
                Wrappers.<Permission>lambdaQuery().orderByAsc(Permission::getId));

        // 按 module 分组，保持 PermissionConst.MODULE_NAMES 的展示顺序
        Map<String, List<PermissionItemVO>> grouped = new LinkedHashMap<>();
        for (String module : PermissionConst.MODULE_NAMES.keySet()) {
            grouped.put(module, new ArrayList<>());
        }
        for (Permission permission : permissions) {
            grouped.computeIfAbsent(permission.getModule(), key -> new ArrayList<>())
                    .add(new PermissionItemVO(permission.getId(), permission.getCode(), permission.getName()));
        }

        List<PermissionGroupVO> groups = new ArrayList<>();
        grouped.forEach((module, items) -> {
            if (!items.isEmpty()) {
                groups.add(new PermissionGroupVO(module, PermissionConst.moduleName(module), items));
            }
        });
        return groups;
    }

    // ------------------------------------------------------------

    private Role requireRole(Long roleId) {
        Role role = roleId == null ? null : roleMapper.selectById(roleId);
        return BizException.requireFound(role, "角色不存在");
    }

    /** {@code [{roleId, cnt}]} → {@code {roleId: cnt}} */
    private static Map<Long, Long> toCountMap(List<Map<String, Object>> rows) {
        Map<Long, Long> map = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object roleId = row.get("roleId");
            Object cnt = row.get("cnt");
            if (roleId instanceof Number id && cnt instanceof Number count) {
                map.put(id.longValue(), count.longValue());
            }
        }
        return map;
    }
}
