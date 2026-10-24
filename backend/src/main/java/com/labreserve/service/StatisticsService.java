package com.labreserve.service;

import com.labreserve.model.dto.StatisticsQueryDTO;
import com.labreserve.model.vo.ExperimentTypeRatioVO;
import com.labreserve.model.vo.OccupancyVO;
import com.labreserve.model.vo.ReservationTrendVO;
import com.labreserve.model.vo.ResourceRatioVO;
import com.labreserve.model.vo.StatisticsOverviewVO;
import com.labreserve.model.vo.SuccessRateVO;

/**
 * 统计报表服务（迭代 8，{@code api.md} §9）。
 *
 * <p>口径统一在服务层实现，前端只负责画图；导出复用同一份聚合结果，
 * 保证「页面数字 = Excel 数字」。
 */
public interface StatisticsService {

    /** 首页总览卡片 */
    StatisticsOverviewVO overview();

    /** 资源占用率（已占用时段 / 开放时段），带 10 分钟 Redis 缓存 */
    OccupancyVO occupancy(StatisticsQueryDTO query);

    /** 实验类型占比 */
    ExperimentTypeRatioVO experimentTypeRatio(StatisticsQueryDTO query);

    /** 预约成功率（APPROVED / 提交总数） */
    SuccessRateVO successRate(StatisticsQueryDTO query);

    /** 资源预约占比（Top N） */
    ResourceRatioVO resourceRatio(StatisticsQueryDTO query);

    /** 预约趋势（day / week / month） */
    ReservationTrendVO reservationTrend(StatisticsQueryDTO query);

    /**
     * 导出 Excel（5 种报表）。
     *
     * @param query {@code type} 决定导出哪种
     * @return 建议的文件名
     */
    String export(StatisticsQueryDTO query, java.io.OutputStream outputStream);
}
