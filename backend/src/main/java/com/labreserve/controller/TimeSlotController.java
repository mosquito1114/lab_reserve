package com.labreserve.controller;

import com.labreserve.common.annotation.OpLog;
import com.labreserve.common.constant.PermissionConst;
import com.labreserve.common.enums.OperationModule;
import com.labreserve.common.enums.OperationType;
import com.labreserve.common.result.Result;
import com.labreserve.model.dto.DictQueryDTO;
import com.labreserve.model.dto.IdDTO;
import com.labreserve.model.dto.TimeSlotSaveDTO;
import com.labreserve.model.vo.TimeSlotVO;
import com.labreserve.security.RequirePerm;
import com.labreserve.service.TimeSlotService;
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
 * 固定时段字典接口（{@code api.md} §3.2）。
 */
@RestController
@RequestMapping("/api/time_slot")
@Tag(name = "05-资源管理")
public class TimeSlotController {

    @Autowired
    private TimeSlotService timeSlotService;

    @Operation(summary = "时段列表", description = "按 sort 升序；可按状态过滤")
    @RequirePerm(PermissionConst.DICT_LIST)
    @PostMapping("/get_list")
    public Result<List<TimeSlotVO>> getList(@RequestBody(required = false) DictQueryDTO dto) {
        return Result.ok(timeSlotService.list(dto));
    }

    @Operation(summary = "新增时段", description = "编码唯一，结束时间必须晚于开始时间")
    @RequirePerm(PermissionConst.DICT_MANAGE)
    @OpLog(module = OperationModule.RESOURCE, type = OperationType.CREATE, content = "新增时段")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody @Valid TimeSlotSaveDTO dto) {
        return Result.ok(timeSlotService.create(dto));
    }

    @Operation(summary = "修改时段")
    @RequirePerm(PermissionConst.DICT_MANAGE)
    @OpLog(module = OperationModule.RESOURCE, type = OperationType.UPDATE, content = "修改时段")
    @PostMapping("/update")
    public Result<Void> update(@RequestBody @Valid TimeSlotSaveDTO dto) {
        timeSlotService.update(dto);
        return Result.ok();
    }

    @Operation(summary = "删除时段", description = "被预约引用时返回 409")
    @RequirePerm(PermissionConst.DICT_MANAGE)
    @OpLog(module = OperationModule.RESOURCE, type = OperationType.DELETE, content = "删除时段")
    @PostMapping("/delete")
    public Result<Void> delete(@RequestBody @Valid IdDTO dto) {
        timeSlotService.delete(dto.getId());
        return Result.ok();
    }
}
