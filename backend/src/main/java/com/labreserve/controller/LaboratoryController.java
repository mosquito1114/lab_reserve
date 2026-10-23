package com.labreserve.controller;

import com.labreserve.common.annotation.OpLog;
import com.labreserve.common.constant.PermissionConst;
import com.labreserve.common.enums.OperationModule;
import com.labreserve.common.enums.OperationType;
import com.labreserve.common.result.PageResult;
import com.labreserve.common.result.Result;
import com.labreserve.model.dto.AvailabilityQueryDTO;
import com.labreserve.model.dto.IdDTO;
import com.labreserve.model.dto.LaboratoryOptionQueryDTO;
import com.labreserve.model.dto.LaboratoryPageDTO;
import com.labreserve.model.dto.LaboratorySaveDTO;
import com.labreserve.model.dto.LaboratoryStatusDTO;
import com.labreserve.model.vo.LaboratoryAvailabilityVO;
import com.labreserve.model.vo.LaboratoryDetailVO;
import com.labreserve.model.vo.LaboratoryOptionVO;
import com.labreserve.model.vo.LaboratoryVO;
import com.labreserve.security.RequirePerm;
import com.labreserve.service.LaboratoryService;
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
 * 实验室接口（{@code api.md} §3.3）。
 *
 * <p>写操作都要求填「更新原因」，服务层会写资源更新日志（before/after 快照）。
 */
@RestController
@RequestMapping("/api/laboratory")
@Tag(name = "05-资源管理")
public class LaboratoryController {

    @Autowired
    private LaboratoryService laboratoryService;

    @Operation(summary = "实验室分页", description = "支持编号/名称/位置关键字、实验类型、最小容量、状态筛选")
    @RequirePerm(PermissionConst.RESOURCE_LAB_LIST)
    @PostMapping("/get_page")
    public Result<PageResult<LaboratoryVO>> getPage(@RequestBody LaboratoryPageDTO dto) {
        return Result.ok(laboratoryService.page(dto));
    }

    @Operation(summary = "实验室详情", description = "含关联设备列表")
    @RequirePerm(PermissionConst.RESOURCE_LAB_LIST)
    @PostMapping("/get_info")
    public Result<LaboratoryDetailVO> getInfo(@RequestBody @Valid IdDTO dto) {
        return Result.ok(laboratoryService.getInfo(dto.getId()));
    }

    @Operation(summary = "新增实验室", description = "编号格式 LAB-XX 且唯一；必须填更新原因")
    @RequirePerm(PermissionConst.RESOURCE_LAB_CREATE)
    @OpLog(module = OperationModule.RESOURCE, type = OperationType.CREATE, content = "新增实验室")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody @Valid LaboratorySaveDTO dto) {
        return Result.ok(laboratoryService.create(dto));
    }

    @Operation(summary = "修改实验室", description = "必须填更新原因，会留 before/after 快照")
    @RequirePerm(PermissionConst.RESOURCE_LAB_UPDATE)
    @OpLog(module = OperationModule.RESOURCE, type = OperationType.UPDATE, content = "修改实验室")
    @PostMapping("/update")
    public Result<Void> update(@RequestBody @Valid LaboratorySaveDTO dto) {
        laboratoryService.update(dto);
        return Result.ok();
    }

    @Operation(summary = "停用 / 启用实验室", description = "必须填更新原因")
    @RequirePerm(PermissionConst.RESOURCE_LAB_DISABLE)
    @OpLog(module = OperationModule.RESOURCE, type = OperationType.UPDATE, content = "变更实验室状态")
    @PostMapping("/update_status")
    public Result<Void> updateStatus(@RequestBody @Valid LaboratoryStatusDTO dto) {
        laboratoryService.updateStatus(dto);
        return Result.ok();
    }

    @Operation(summary = "实验室下拉", description = "登录即可；默认只返回可用实验室")
    @PostMapping("/list_option")
    public Result<List<LaboratoryOptionVO>> listOption(@RequestBody(required = false) LaboratoryOptionQueryDTO dto) {
        return Result.ok(laboratoryService.listOption(dto));
    }

    @Operation(summary = "实验室某日可用时段", description = "登录即可；预约页用，返回可用/占用原因/排队人数/我的顺位")
    @PostMapping("/get_availability")
    public Result<LaboratoryAvailabilityVO> getAvailability(@RequestBody @Valid AvailabilityQueryDTO dto) {
        return Result.ok(laboratoryService.availability(dto));
    }
}
