package com.labreserve.controller;

import com.labreserve.common.annotation.OpLog;
import com.labreserve.common.constant.PermissionConst;
import com.labreserve.common.enums.OperationModule;
import com.labreserve.common.enums.OperationType;
import com.labreserve.common.result.PageResult;
import com.labreserve.common.result.Result;
import com.labreserve.model.dto.IdDTO;
import com.labreserve.model.dto.RepairAcceptDTO;
import com.labreserve.model.dto.RepairAssignDTO;
import com.labreserve.model.dto.RepairCreateDTO;
import com.labreserve.model.dto.RepairPageDTO;
import com.labreserve.model.dto.RepairProgressDTO;
import com.labreserve.model.dto.RepairReviewDTO;
import com.labreserve.model.vo.RepairOrderDetailVO;
import com.labreserve.model.vo.RepairOrderVO;
import com.labreserve.security.RequirePerm;
import com.labreserve.service.RepairService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 设备报修工单（{@code api.md} §7.2）。
 */
@RestController
@RequestMapping("/api/repair_order")
@Tag(name = "09-设备运维")
public class RepairOrderController {

    @Autowired
    private RepairService repairService;

    @Operation(summary = "提交报修", description = "自动通知所有实验室管理员复核")
    @RequirePerm(PermissionConst.REPAIR_CREATE)
    @OpLog(module = OperationModule.REPAIR, type = OperationType.CREATE, content = "提交报修工单")
    @PostMapping("/create")
    public Result<RepairOrderVO> create(@RequestBody @Valid RepairCreateDTO dto) {
        return Result.ok(repairService.create(dto));
    }

    @Operation(summary = "工单分页", description = "管理员看全部；无 repair:list 的角色只能看自己提交的")
    @RequirePerm(PermissionConst.REPAIR_LIST)
    @PostMapping("/get_page")
    public Result<PageResult<RepairOrderVO>> getPage(@RequestBody RepairPageDTO dto) {
        return Result.ok(repairService.page(dto));
    }

    @Operation(summary = "我的报修（报修人视角）", description = "登录即可，只看自己提交的工单")
    @PostMapping("/get_my_submitted_page")
    public Result<PageResult<RepairOrderVO>> getMySubmittedPage(@RequestBody RepairPageDTO dto) {
        dto.setOnlyMine(true);
        return Result.ok(repairService.page(dto));
    }

    @Operation(summary = "我的维修任务", description = "维修人：只看指派给自己的")
    @RequirePerm(PermissionConst.REPAIR_LIST_ASSIGNED)
    @PostMapping("/get_my_assigned_page")
    public Result<PageResult<RepairOrderVO>> getMyAssignedPage(@RequestBody RepairPageDTO dto) {
        return Result.ok(repairService.myAssignedPage(dto));
    }

    @Operation(summary = "工单详情", description = "含故障图片与维修记录时间线")
    @PostMapping("/get_info")
    public Result<RepairOrderDetailVO> getInfo(@RequestBody @Valid IdDTO dto) {
        return Result.ok(repairService.getInfo(dto.getId()));
    }

    @Operation(summary = "复核工单", description = "确认故障（可改优先级/设备状态）或驳回")
    @RequirePerm(PermissionConst.REPAIR_REVIEW)
    @OpLog(module = OperationModule.REPAIR, type = OperationType.AUDIT, content = "复核报修工单")
    @PostMapping("/review")
    public Result<Void> review(@RequestBody @Valid RepairReviewDTO dto) {
        repairService.review(dto);
        return Result.ok();
    }

    @Operation(summary = "派单", description = "只能派给 MAINTAINER 且账号正常")
    @RequirePerm(PermissionConst.REPAIR_ASSIGN)
    @OpLog(module = OperationModule.REPAIR, type = OperationType.ASSIGN, content = "派单")
    @PostMapping("/assign")
    public Result<Void> assign(@RequestBody @Valid RepairAssignDTO dto) {
        repairService.assign(dto);
        return Result.ok();
    }

    @Operation(summary = "更新维修进度", description = "只有被指派的维修人能操作")
    @RequirePerm(PermissionConst.REPAIR_PROGRESS)
    @OpLog(module = OperationModule.REPAIR, type = OperationType.UPDATE, content = "更新维修进度")
    @PostMapping("/update_progress")
    public Result<Void> updateProgress(@RequestBody @Valid RepairProgressDTO dto) {
        repairService.updateProgress(dto);
        return Result.ok();
    }

    @Operation(summary = "维修完成", description = "填写维修说明与配件，通知管理员验收")
    @RequirePerm(PermissionConst.REPAIR_PROGRESS)
    @OpLog(module = OperationModule.REPAIR, type = OperationType.UPDATE, content = "完成维修")
    @PostMapping("/complete")
    public Result<Void> complete(@RequestBody @Valid RepairProgressDTO dto) {
        repairService.complete(dto);
        return Result.ok();
    }

    @Operation(summary = "验收", description = "PASS：归档 + 设备恢复正常；FAIL：重开给维修人")
    @RequirePerm(PermissionConst.REPAIR_ACCEPT)
    @OpLog(module = OperationModule.REPAIR, type = OperationType.ACCEPT, content = "验收报修工单")
    @PostMapping("/accept")
    public Result<Void> accept(@RequestBody @Valid RepairAcceptDTO dto) {
        repairService.accept(dto);
        return Result.ok();
    }
}
