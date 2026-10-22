package com.labreserve.controller;

import com.labreserve.common.constant.PermissionConst;
import com.labreserve.common.result.PageResult;
import com.labreserve.common.result.Result;
import com.labreserve.model.dto.ResourceLogPageDTO;
import com.labreserve.model.vo.ResourceLogVO;
import com.labreserve.security.RequirePerm;
import com.labreserve.service.ResourceLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 资源更新日志接口（{@code api.md} §3.5）。只读，日志由资源写操作自动生成。
 */
@RestController
@RequestMapping("/api/resource_log")
@Tag(name = "05-资源管理")
public class ResourceLogController {

    @Autowired
    private ResourceLogService resourceLogService;

    @Operation(summary = "资源更新日志分页", description = "含 before / after 快照对比")
    @RequirePerm(PermissionConst.RESOURCE_LOG_LIST)
    @PostMapping("/get_page")
    public Result<PageResult<ResourceLogVO>> getPage(@RequestBody ResourceLogPageDTO dto) {
        return Result.ok(resourceLogService.page(dto));
    }
}
