package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 调整申请（列表 / 详情共用）。
 */
@Data
@Schema(description = "调整申请")
public class ReservationChangeVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long reservationId;

    @Schema(description = "预约号")
    private String reserveNo;

    private Long applicantId;

    @Schema(description = "申请人姓名")
    private String applicantName;

    @Schema(description = "1 修改 / 2 取消")
    private Integer changeType;

    private String changeTypeName;

    private String reason;

    @Schema(description = "0 待审批 / 1 通过 / 2 驳回")
    private Integer status;

    private String statusName;

    @Schema(description = "变更前快照（JSON）")
    private String beforeJson;

    @Schema(description = "变更后快照（JSON）")
    private String afterJson;

    @Schema(description = "变更摘要（人话，前端直接展示）", example = "2026-10-25 3-4节 → 2026-10-26 5-6节")
    private String summary;

    private Long auditUserId;

    @Schema(description = "审批人姓名")
    private String auditUserName;

    private LocalDateTime auditTime;

    private String auditOpinion;

    private LocalDateTime createdAt;
}
