package com.labreserve.controller;

import com.labreserve.common.annotation.OpLog;
import com.labreserve.common.constant.PermissionConst;
import com.labreserve.common.enums.OperationModule;
import com.labreserve.common.enums.OperationType;
import com.labreserve.common.result.PageResult;
import com.labreserve.common.result.Result;
import com.labreserve.model.dto.AvailabilityQueryDTO;
import com.labreserve.model.dto.EquipmentOptionQueryDTO;
import com.labreserve.model.dto.EquipmentPageDTO;
import com.labreserve.model.dto.EquipmentSaveDTO;
import com.labreserve.model.dto.EquipmentStatusDTO;
import com.labreserve.model.dto.IdDTO;
import com.labreserve.model.vo.EquipmentAvailabilityVO;
import com.labreserve.model.vo.EquipmentOptionVO;
import com.labreserve.model.vo.EquipmentVO;
import com.labreserve.security.RequirePerm;
import com.labreserve.service.EquipmentService;
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
 * 设备接口（{@code api.md} §3.4）。
 */
@RestController
@RequestMapping("/api/equipment")
@Tag(name = "05-资源管理")
public class EquipmentController {

    @Autowired
    private EquipmentService equipmentService;

    @Operation(summary = "设备分页")
    @RequirePerm(PermissionConst.RESOURCE_EQUIP_LIST)
    @PostMapping("/get_page")
    public Result<PageResult<EquipmentVO>> getPage(@RequestBody EquipmentPageDTO dto) {
        return Result.ok(equipmentService.page(dto));
    }

    @Operation(summary = "设备详情")
    @RequirePerm(PermissionConst.RESOURCE_EQUIP_LIST)
    @PostMapping("/get_info")
    public Result<EquipmentVO> getInfo(@RequestBody @Valid IdDTO dto) {
        return Result.ok(equipmentService.getInfo(dto.getId()));
    }

    @Operation(summary = "新增设备", description = "编号格式 ZY-XX-YYY 且唯一；必须填更新原因")
    @RequirePerm(PermissionConst.RESOURCE_EQUIP_CREATE)
    @OpLog(module = OperationModule.RESOURCE, type = OperationType.CREATE, content = "新增设备")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody @Valid EquipmentSaveDTO dto) {
        return Result.ok(equipmentService.create(dto));
    }

    @Operation(summary = "修改设备", description = "必须填更新原因")
    @RequirePerm(PermissionConst.RESOURCE_EQUIP_UPDATE)
    @OpLog(module = OperationModule.RESOURCE, type = OperationType.UPDATE, content = "修改设备")
    @PostMapping("/update")
    public Result<Void> update(@RequestBody @Valid EquipmentSaveDTO dto) {
        equipmentService.update(dto);
        return Result.ok();
    }

    @Operation(summary = "设备状态变更", description = "1 正常 / 2 维修中 / 3 报废 / 0 停用；必须填更新原因")
    @RequirePerm(PermissionConst.RESOURCE_EQUIP_DISABLE)
    @OpLog(module = OperationModule.RESOURCE, type = OperationType.UPDATE, content = "变更设备状态")
    @PostMapping("/update_status")
    public Result<Void> updateStatus(@RequestBody @Valid EquipmentStatusDTO dto) {
        equipmentService.updateStatus(dto);
        return Result.ok();
    }

    @Operation(summary = "设备下拉", description = "登录即可；可按实验室过滤")
    @PostMapping("/list_option")
    public Result<List<EquipmentOptionVO>> listOption(@RequestBody(required = false) EquipmentOptionQueryDTO dto) {
        return Result.ok(equipmentService.listOption(dto));
    }

    @Operation(summary = "设备某日各时段剩余库存", description = "登录即可；返回已占用与剩余可借数量")
    @PostMapping("/get_availability")
    public Result<EquipmentAvailabilityVO> getAvailability(@RequestBody @Valid AvailabilityQueryDTO dto) {
        return Result.ok(equipmentService.availability(dto));
    }
}
