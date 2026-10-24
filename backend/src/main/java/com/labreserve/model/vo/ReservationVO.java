package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 预约列表项。
 */
@Data
@Schema(description = "预约")
public class ReservationVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    @Schema(description = "预约号", example = "R-20261025-0007")
    private String reserveNo;

    @Schema(description = "状态", example = "PENDING")
    private String status;

    @Schema(description = "状态中文名", example = "待审核")
    private String statusName;

    private Long applicantId;

    @Schema(description = "申请人姓名")
    private String applicantName;

    @Schema(description = "申请人学院")
    private String applicantCollege;

    @Schema(description = "1 实验室 / 2 设备")
    private Integer resourceType;

    private String resourceName;

    private Long labId;

    private String labNo;

    private String labLocation;

    private Long equipmentId;

    private String equipmentNo;

    private Integer equipmentCount;

    private LocalDate reserveDate;

    private Long slotId;

    private String slotName;

    private LocalTime startTime;

    private LocalTime endTime;

    private Integer usePeople;

    private Long experimentTypeId;

    private String experimentTypeName;

    private String courseName;

    private String purpose;

    private LocalDateTime createdAt;
}
