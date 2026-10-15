package com.labreserve.controller;

import com.labreserve.common.constant.PermissionConst;
import com.labreserve.common.result.Result;
import com.labreserve.model.vo.PermissionGroupVO;
import com.labreserve.security.RequirePerm;
import com.labreserve.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 权限点接口（{@code api.md} §10.2）。
 *
 * <p>只读：权限点是系统内置数据，界面上只能勾选到角色上，不能增删改。
 */
@RestController
@RequestMapping("/api/permission")
@Tag(name = "03-系统管理")
public class PermissionController {

    @Autowired
    private RoleService roleService;

    @Operation(summary = "权限点列表", description = "按 module 分组，供权限勾选树渲染")
    @RequirePerm(PermissionConst.SYSTEM_PERMISSION_LIST)
    @PostMapping("/get_list")
    public Result<List<PermissionGroupVO>> getList() {
        return Result.ok(roleService.listPermissionGroups());
    }
}
