package com.labreserve.controller;

import com.alibaba.excel.EasyExcel;
import com.labreserve.common.annotation.OpLog;
import com.labreserve.common.constant.PermissionConst;
import com.labreserve.common.enums.OperationModule;
import com.labreserve.common.enums.OperationType;
import com.labreserve.common.result.PageResult;
import com.labreserve.common.result.Result;
import com.labreserve.model.dto.IdDTO;
import com.labreserve.model.dto.OperationLogPageDTO;
import com.labreserve.model.vo.OperationLogExportVO;
import com.labreserve.model.vo.OperationLogVO;
import com.labreserve.security.RequirePerm;
import com.labreserve.service.OperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 操作日志接口（{@code api.md} §10.3）。
 *
 * <p>审计语义：<b>只读</b>——不提供新增 / 修改 / 删除接口（需求 Q28），
 * 日志由 {@code @OpLog} 切面自动写入。
 */
@Slf4j
@RestController
@RequestMapping("/api/operation_log")
@Tag(name = "03-系统管理")
public class OperationLogController {

    private static final DateTimeFormatter FILE_NAME_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired
    private OperationLogService operationLogService;

    @Operation(summary = "操作日志分页查询", description = "多条件筛选：类型 / 模块 / 操作人 / 时间区间 / IP / 结果")
    @RequirePerm(PermissionConst.SYSTEM_LOG_LIST)
    @PostMapping("/get_page")
    public Result<PageResult<OperationLogVO>> getPage(@RequestBody OperationLogPageDTO dto) {
        return Result.ok(operationLogService.page(dto));
    }

    @Operation(summary = "操作日志详情")
    @RequirePerm(PermissionConst.SYSTEM_LOG_LIST)
    @PostMapping("/get_info")
    public Result<OperationLogVO> getInfo(@RequestBody @Valid IdDTO dto) {
        return Result.ok(operationLogService.getInfo(dto.getId()));
    }

    @Operation(summary = "操作日志导出 Excel", description = "筛选条件与列表一致，返回 xlsx 文件流")
    @RequirePerm(PermissionConst.SYSTEM_LOG_EXPORT)
    @OpLog(module = OperationModule.SYSTEM, type = OperationType.EXPORT, content = "导出操作日志")
    @PostMapping("/export")
    public void export(@RequestBody OperationLogPageDTO dto, HttpServletResponse response) throws IOException {
        List<OperationLogExportVO> rows = operationLogService.listForExport(dto);

        String fileName = "操作日志_" + LocalDateTime.now().format(FILE_NAME_TIME) + ".xlsx";
        response.setContentType(CONTENT_TYPE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // 中文文件名需要 URL 编码，否则浏览器下载后是乱码
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''"
                + URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20"));

        EasyExcel.write(response.getOutputStream(), OperationLogExportVO.class)
                .sheet("操作日志")
                .doWrite(rows);
        log.info("[操作日志] 导出 {} 条，文件名={}", rows.size(), fileName);
    }
}
