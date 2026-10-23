package com.labreserve.controller;

import com.labreserve.common.annotation.OpLog;
import com.labreserve.common.constant.PermissionConst;
import com.labreserve.common.enums.OperationModule;
import com.labreserve.common.enums.OperationType;
import com.labreserve.common.result.Result;
import com.labreserve.model.dto.DictQueryDTO;
import com.labreserve.model.dto.ExperimentTypeSaveDTO;
import com.labreserve.model.dto.IdDTO;
import com.labreserve.model.vo.ExperimentTypeVO;
import com.labreserve.security.RequirePerm;
import com.labreserve.service.ExperimentTypeService;
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
 * 实验类型字典接口（{@code api.md} §3.1）。
 */
@RestController
@RequestMapping("/api/experiment_type")
@Tag(name = "05-资源管理")
public class ExperimentTypeController {

    @Autowired
    private ExperimentTypeService experimentTypeService;

    @Operation(summary = "实验类型列表", description = "按 sort 升序；可按状态过滤")
    @RequirePerm(PermissionConst.DICT_LIST)
    @PostMapping("/get_list")
    public Result<List<ExperimentTypeVO>> getList(@RequestBody(required = false) DictQueryDTO dto) {
        return Result.ok(experimentTypeService.list(dto));
    }

    @Operation(summary = "新增实验类型", description = "编码唯一")
    @RequirePerm(PermissionConst.DICT_MANAGE)
    @OpLog(module = OperationModule.RESOURCE, type = OperationType.CREATE, content = "新增实验类型")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody @Valid ExperimentTypeSaveDTO dto) {
        return Result.ok(experimentTypeService.create(dto));
    }

    @Operation(summary = "修改实验类型")
    @RequirePerm(PermissionConst.DICT_MANAGE)
    @OpLog(module = OperationModule.RESOURCE, type = OperationType.UPDATE, content = "修改实验类型")
    @PostMapping("/update")
    public Result<Void> update(@RequestBody @Valid ExperimentTypeSaveDTO dto) {
        experimentTypeService.update(dto);
        return Result.ok();
    }

    @Operation(summary = "删除实验类型", description = "被实验室/预约引用时返回 409")
    @RequirePerm(PermissionConst.DICT_MANAGE)
    @OpLog(module = OperationModule.RESOURCE, type = OperationType.DELETE, content = "删除实验类型")
    @PostMapping("/delete")
    public Result<Void> delete(@RequestBody @Valid IdDTO dto) {
        experimentTypeService.delete(dto.getId());
        return Result.ok();
    }
}
