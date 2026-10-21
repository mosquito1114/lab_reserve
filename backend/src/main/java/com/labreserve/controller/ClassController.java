package com.labreserve.controller;

import com.labreserve.common.annotation.OpLog;
import com.labreserve.common.constant.PermissionConst;
import com.labreserve.common.enums.OperationModule;
import com.labreserve.common.enums.OperationType;
import com.labreserve.common.result.PageResult;
import com.labreserve.common.result.Result;
import com.labreserve.model.dto.ClassListDTO;
import com.labreserve.model.dto.ClassPageDTO;
import com.labreserve.model.dto.ClassSaveDTO;
import com.labreserve.model.vo.ClassVO;
import com.labreserve.security.RequirePerm;
import com.labreserve.service.ClassService;
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
 * 班级接口（{@code api.md} §2.9）。
 *
 * <p>班级既是学生学院/专业的来源，也是教师排课选班级的数据源：
 * {@code get_list} 所有登录用户可用（下拉），维护类接口需要 {@code class:manage}。
 */
@RestController
@RequestMapping("/api/class")
@Tag(name = "04-用户管理")
public class ClassController {

    @Autowired
    private ClassService classService;

    @Operation(summary = "班级下拉", description = "只返回启用状态；教师选上课班级用")
    @RequirePerm(PermissionConst.CLASS_LIST)
    @PostMapping("/get_list")
    public Result<List<ClassVO>> getList(@RequestBody ClassListDTO dto) {
        return Result.ok(classService.list(dto));
    }

    @Operation(summary = "班级分页", description = "管理员维护用，含停用班级")
    @RequirePerm(PermissionConst.CLASS_MANAGE)
    @PostMapping("/get_page")
    public Result<PageResult<ClassVO>> getPage(@RequestBody ClassPageDTO dto) {
        return Result.ok(classService.page(dto));
    }

    @Operation(summary = "新增班级", description = "班级名称全局唯一")
    @RequirePerm(PermissionConst.CLASS_MANAGE)
    @OpLog(module = OperationModule.USER, type = OperationType.CREATE, content = "新增班级")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody @Valid ClassSaveDTO dto) {
        return Result.ok(classService.create(dto));
    }

    @Operation(summary = "修改班级")
    @RequirePerm(PermissionConst.CLASS_MANAGE)
    @OpLog(module = OperationModule.USER, type = OperationType.UPDATE, content = "修改班级")
    @PostMapping("/update")
    public Result<Void> update(@RequestBody @Valid ClassSaveDTO dto) {
        classService.update(dto);
        return Result.ok();
    }
}
