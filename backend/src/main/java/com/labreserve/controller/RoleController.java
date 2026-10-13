package com.labreserve.controller;

import com.labreserve.common.annotation.OpLog;
import com.labreserve.common.constant.PermissionConst;
import com.labreserve.common.enums.OperationModule;
import com.labreserve.common.enums.OperationType;
import com.labreserve.common.result.Result;
import com.labreserve.model.dto.RolePermissionUpdateDTO;
import com.labreserve.model.dto.RoleQueryDTO;
import com.labreserve.model.vo.PermissionGroupVO;
import com.labreserve.model.vo.RolePermissionVO;
import com.labreserve.model.vo.RoleVO;
import com.labreserve.security.RequirePerm;
import com.labreserve.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色与权限点接口（{@code api.md} §10）。
 *
 * <p>角色不能增删，能改的只有「角色拥有哪些权限」——
 * 权限是数据（{@code tb_role_permission}），勾选保存后立即生效（Redis 缓存同步失效）。
 */
@RestController
@RequestMapping("/api/role")
@Tag(name = "03-系统管理")
public class RoleController {

    @Autowired
    private RoleService roleService;

    @Operation(summary = "角色列表", description = "含每个角色的权限数与用户数")
    @RequirePerm(PermissionConst.SYSTEM_ROLE_LIST)
    @PostMapping("/get_list")
    public Result<List<RoleVO>> getList() {
        return Result.ok(roleService.listRoles());
    }

    @Operation(summary = "角色的权限清单", description = "用于权限勾选树回显")
    @RequirePerm(PermissionConst.SYSTEM_ROLE_LIST)
    @PostMapping("/get_permission_list")
    public Result<RolePermissionVO> getPermissionList(@RequestBody @Valid RoleQueryDTO dto) {
        return Result.ok(roleService.getRolePermissions(dto.getRoleId()));
    }

    @Operation(summary = "保存角色权限", description = "全量覆盖；不允许移除系统管理员的 system:role:update")
    @RequirePerm(PermissionConst.SYSTEM_ROLE_UPDATE)
    @OpLog(module = OperationModule.SYSTEM, type = OperationType.UPDATE, content = "保存角色权限")
    @PostMapping("/update_permission")
    public Result<Void> updatePermission(@RequestBody @Valid RolePermissionUpdateDTO dto) {
        roleService.updateRolePermissions(dto);
        return Result.ok();
    }
}
