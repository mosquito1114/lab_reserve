package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 排队记录（列表 / 详情共用，带实时顺位）。
 */
@Data
@Schema(description = "排队记录")
public class QueueVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    @Schema(description = "排队号", example = "P-LAB-01-20261027-003")
    private String queueNo;

    private Integer resourceType;

    private Long labId;

    private Long equipmentId;

    @Schema(description = "资源名称", example = "LAB-01 计算机基础实验室")
    private String resourceName;

    private LocalDate reserveDate;

    private Long slotId;

    private String slotName;

    private LocalTime startTime;

    private LocalTime endTime;

    private Long applicantId;

    @Schema(description = "申请人姓名")
    private String applicantName;

    @Schema(description = "状态：QUEUING / NOTIFIED / CONFIRMED / ABANDONED / TIMEOUT / CANCELED")
    private String status;

    private String statusName;

    @Schema(description = "实时顺位（ZRANK + 1）")
    private Integer position;

    @Schema(description = "队内总人数")
    private Long totalCount;

    @Schema(description = "前面还有几个人")
    private Integer aheadCount;

    @Schema(description = "候补确认截止时间（仅 NOTIFIED 有值）")
    private LocalDateTime deadline;

    @Schema(description = "距截止还有多少秒（前端倒计时用；负数表示已超时）")
    private Long remainSeconds;

    private LocalDateTime joinTime;

    private LocalDateTime createdAt;
}
