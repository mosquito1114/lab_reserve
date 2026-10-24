package com.labreserve.controller;

import com.labreserve.common.annotation.OpLog;
import com.labreserve.common.constant.PermissionConst;
import com.labreserve.common.enums.OperationModule;
import com.labreserve.common.enums.OperationType;
import com.labreserve.common.result.Result;
import com.labreserve.model.dto.StatisticsQueryDTO;
import com.labreserve.model.vo.ExperimentTypeRatioVO;
import com.labreserve.model.vo.OccupancyVO;
import com.labreserve.model.vo.ReservationTrendVO;
import com.labreserve.model.vo.ResourceRatioVO;
import com.labreserve.model.vo.StatisticsOverviewVO;
import com.labreserve.model.vo.SuccessRateVO;
import com.labreserve.security.RequirePerm;
import com.labreserve.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 统计报表（{@code api.md} §9）。
 *
 * <p>口径统一在服务层实现，导出与页面数字同源，保证「所见即所得」。
 */
@RestController
@RequestMapping("/api/statistics")
@Tag(name = "10-数据统计")
public class StatisticsController {

    private static final String CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final DateTimeFormatter FILE_NAME_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Autowired
    private StatisticsService statisticsService;

    @Operation(summary = "首页总览", description = "预约总数 / 待审核 / 今日预约 / 排队中 / 待处理工单 / 未读消息")
    @RequirePerm(PermissionConst.STAT_VIEW)
    @PostMapping("/get_overview")
    public Result<StatisticsOverviewVO> getOverview() {
        return Result.ok(statisticsService.overview());
    }

    @Operation(summary = "资源占用率", description = "已占用时段 / 开放时段；无筛选时缓存 10 分钟")
    @RequirePerm(PermissionConst.STAT_VIEW)
    @PostMapping("/get_occupancy")
    public Result<OccupancyVO> getOccupancy(@RequestBody(required = false) StatisticsQueryDTO dto) {
        return Result.ok(statisticsService.occupancy(dto));
    }

    @Operation(summary = "实验类型占比")
    @RequirePerm(PermissionConst.STAT_VIEW)
    @PostMapping("/get_experiment_type_ratio")
    public Result<ExperimentTypeRatioVO> getExperimentTypeRatio(@RequestBody(required = false) StatisticsQueryDTO dto) {
        return Result.ok(statisticsService.experimentTypeRatio(dto));
    }

    @Operation(summary = "预约成功率", description = "APPROVED / 提交总数")
    @RequirePerm(PermissionConst.STAT_VIEW)
    @PostMapping("/get_success_rate")
    public Result<SuccessRateVO> getSuccessRate(@RequestBody(required = false) StatisticsQueryDTO dto) {
        return Result.ok(statisticsService.successRate(dto));
    }

    @Operation(summary = "资源预约占比")
    @RequirePerm(PermissionConst.STAT_VIEW)
    @PostMapping("/get_resource_ratio")
    public Result<ResourceRatioVO> getResourceRatio(@RequestBody(required = false) StatisticsQueryDTO dto) {
        return Result.ok(statisticsService.resourceRatio(dto));
    }

    @Operation(summary = "预约趋势", description = "groupBy=day/week/month")
    @RequirePerm(PermissionConst.STAT_VIEW)
    @PostMapping("/get_reservation_trend")
    public Result<ReservationTrendVO> getReservationTrend(@RequestBody(required = false) StatisticsQueryDTO dto) {
        return Result.ok(statisticsService.reservationTrend(dto));
    }

    @Operation(summary = "导出 Excel", description = "type=reservation/occupancy/experiment-type/success-rate/resource-ratio")
    @RequirePerm(PermissionConst.STAT_EXPORT)
    @OpLog(module = OperationModule.STATISTICS, type = OperationType.EXPORT, content = "导出统计报表")
    @PostMapping("/export")
    public void export(@RequestBody StatisticsQueryDTO dto, HttpServletResponse response) throws IOException {
        String fileName = "统计报表_" + LocalDateTime.now().format(FILE_NAME_TIME) + ".xlsx";
        response.setContentType(CONTENT_TYPE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // 中文文件名要 URL 编码，否则下载后是乱码
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''"
                + URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20"));
        statisticsService.export(dto, response.getOutputStream());
    }
}
