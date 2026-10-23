package com.labreserve.controller;

import com.labreserve.common.annotation.OpLog;
import com.labreserve.common.constant.PermissionConst;
import com.labreserve.common.enums.OperationModule;
import com.labreserve.common.enums.OperationType;
import com.labreserve.common.result.PageResult;
import com.labreserve.common.result.Result;
import com.labreserve.model.dto.MaintainerQueryDTO;
import com.labreserve.model.dto.UserCreateDTO;
import com.labreserve.model.dto.UserInfoDTO;
import com.labreserve.model.dto.UserOptionQueryDTO;
import com.labreserve.model.dto.UserPageDTO;
import com.labreserve.model.dto.UserUpdateDTO;
import com.labreserve.model.dto.UserUpdateSelfDTO;
import com.labreserve.model.dto.UserUpdateStatusDTO;
import com.labreserve.model.vo.MaintainerVO;
import com.labreserve.model.vo.UserDetailVO;
import com.labreserve.model.vo.UserOptionVO;
import com.labreserve.model.vo.UserVO;
import com.labreserve.security.RequirePerm;
import com.labreserve.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户管理接口（{@code api.md} §2）。
 *
 * <p>权限要点：
 * <ul>
 *   <li>{@code create/update/update_status} 只有 {@code SYS_ADMIN} 有权限——
 *       {@code LAB_ADMIN} 没有 {@code user:create}，调用会直接被 403（需求 Q32）</li>
 *   <li>{@code update_self} / {@code get_self_profile} 只要登录即可，
 *       且<b>没有 id 参数</b>，从 token 取当前用户，从接口层面杜绝改别人</li>
 *   <li>手机号默认脱敏；{@code revealPhone=true} + {@code user:view-phone} 才明文，
 *       并写 {@code QUERY_PHONE} 日志（在 Service 里做，因为要判断「是否本人」）</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/user")
@Tag(name = "04-用户管理")
public class UserController {

    @Autowired
    private UserService userService;

    @Operation(summary = "用户分页查询", description = "关键字/角色/学院/班级/学号工号/状态；revealPhone 需 user:view-phone")
    @RequirePerm(PermissionConst.USER_LIST)
    @PostMapping("/get_page")
    public Result<PageResult<UserVO>> getPage(@RequestBody UserPageDTO dto) {
        return Result.ok(userService.page(dto));
    }

    @Operation(summary = "用户详情", description = "含学生/教师/管理类扩展档案")
    @RequirePerm(PermissionConst.USER_VIEW)
    @PostMapping("/get_info")
    public Result<UserDetailVO> getInfo(@RequestBody @Valid UserInfoDTO dto) {
        return Result.ok(userService.getInfo(dto.getId(), dto.getRevealPhone()));
    }

    @Operation(summary = "新增用户", description = "仅系统管理员；实验室管理员调用返回 403")
    @RequirePerm(PermissionConst.USER_CREATE)
    @OpLog(module = OperationModule.USER, type = OperationType.CREATE, content = "新增用户")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody @Valid UserCreateDTO dto) {
        return Result.ok(userService.create(dto));
    }

    @Operation(summary = "修改用户", description = "不允许修改角色 / 登录账号 / 学号工号")
    @RequirePerm(PermissionConst.USER_UPDATE)
    @OpLog(module = OperationModule.USER, type = OperationType.UPDATE, content = "修改用户")
    @PostMapping("/update")
    public Result<Void> update(@RequestBody @Valid UserUpdateDTO dto) {
        userService.update(dto);
        return Result.ok();
    }

    @Operation(summary = "注销 / 恢复用户", description = "不物理删除；不能注销自己")
    @RequirePerm(PermissionConst.USER_DISABLE)
    @OpLog(module = OperationModule.USER, type = OperationType.UPDATE, content = "注销/恢复用户")
    @PostMapping("/update_status")
    public Result<Void> updateStatus(@RequestBody @Valid UserUpdateStatusDTO dto) {
        userService.updateStatus(dto);
        return Result.ok();
    }

    @Operation(summary = "修改自己的资料", description = "手机号 / 邮箱 / 邮件通知开关")
    @OpLog(module = OperationModule.USER, type = OperationType.UPDATE, content = "修改本人资料")
    @PostMapping("/update_self")
    public Result<Void> updateSelf(@RequestBody @Valid UserUpdateSelfDTO dto) {
        userService.updateSelf(dto);
        return Result.ok();
    }

    @Operation(summary = "我的档案", description = "登录即可；手机号返回明文（本人查看自己）")
    @PostMapping("/get_self_profile")
    public Result<UserDetailVO> getSelfProfile() {
        return Result.ok(userService.getSelfProfile());
    }

    @Operation(summary = "维修人员下拉", description = "派单用；需要 repair:assign")
    @RequirePerm(PermissionConst.REPAIR_ASSIGN)
    @PostMapping("/list_maintainer")
    public Result<List<MaintainerVO>> listMaintainer(@RequestBody MaintainerQueryDTO dto) {
        return Result.ok(userService.listMaintainer(dto));
    }

    @Operation(summary = "用户下拉", description = "按角色过滤，只返回正常状态用户")
    @PostMapping("/list_option")
    public Result<List<UserOptionVO>> listOption(@RequestBody UserOptionQueryDTO dto) {
        return Result.ok(userService.listOption(dto));
    }
}
