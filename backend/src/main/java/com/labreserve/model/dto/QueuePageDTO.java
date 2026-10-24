package com.labreserve.model.dto;

import com.labreserve.common.result.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDate;

/**
 * 排队分页（我的排队 / 管理端某资源队列共用）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "排队分页入参")
public class QueuePageDTO extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "资源类型：1 实验室 / 2 设备")
    private Integer resourceType;

    @Schema(description = "实验室 ID")
    private Long labId;

    @Schema(description = "设备 ID")
    private Long equipmentId;

    @Schema(description = "预约日期（管理端按某天某时段看整条队列时必填）")
    private LocalDate reserveDate;

    @Schema(description = "时段 ID")
    private Long slotId;

    @Schema(description = "状态：QUEUING / NOTIFIED / CONFIRMED / ABANDONED / TIMEOUT / CANCELED")
    private String status;
}
