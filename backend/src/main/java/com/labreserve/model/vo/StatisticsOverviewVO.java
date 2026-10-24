package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 首页总览卡片。
 */
@Data
@Schema(description = "统计总览")
public class StatisticsOverviewVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "预约总数（含历史）")
    private Long reservationTotal;

    @Schema(description = "待审核预约数")
    private Long pendingAudit;

    @Schema(description = "今日预约数")
    private Long todayReservation;

    @Schema(description = "排队中人数（DB 口径，含 QUEUING / NOTIFIED）")
    private Long queueTotal;

    @Schema(description = "待处理工单（未验收且未驳回）")
    private Long repairPending;

    private Long labTotal;

    private Long equipmentTotal;

    @Schema(description = "当前用户未读消息")
    private Long unreadMessage;
}
