package com.labreserve.model.dto;

import com.labreserve.common.result.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 调整申请分页（管理员）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "调整申请分页入参")
public class ReservationChangePageDTO extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "状态：0 待审批 / 1 通过 / 2 驳回")
    private Integer status;

    @Schema(description = "调整类型：1 修改 / 2 取消")
    private Integer changeType;

    @Schema(description = "申请人 ID")
    private Long applicantId;

    @Schema(description = "开始时间")
    private LocalDateTime beginTime;

    @Schema(description = "结束时间")
    private LocalDateTime endTime;
}
