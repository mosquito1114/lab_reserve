package com.labreserve.model.dto;

import com.labreserve.common.result.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 报修工单分页（{@code get_page} / {@code get_my_assigned_page} 共用）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "报修工单分页入参")
public class RepairPageDTO extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "状态：SUBMITTED/REJECTED/ASSIGNED/REPAIRING/REPAIRED/ACCEPTED/REOPENED")
    private String status;

    @Schema(description = "优先级：1 低 / 2 中 / 3 高 / 4 紧急")
    private Integer priority;

    private Long equipmentId;

    @Schema(description = "设备编号模糊匹配", example = "ZY-01")
    private String equipmentNo;

    private Long submitterId;

    private Long assigneeId;

    @Schema(description = "只看我提交的（报修人视角）")
    private Boolean onlyMine;

    private LocalDateTime beginTime;

    private LocalDateTime endTime;
}
