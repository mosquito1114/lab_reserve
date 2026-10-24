package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 报修工单（列表项）。
 */
@Data
@Schema(description = "报修工单")
public class RepairOrderVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String orderNo;

    private Long submitterId;

    private String submitterName;

    private Long equipmentId;

    private String equipmentNo;

    private String equipmentName;

    private String labNo;

    private String faultDesc;

    private String faultScene;

    @Schema(description = "优先级：1 低 / 2 中 / 3 高 / 4 紧急")
    private Integer priority;

    private String priorityName;

    private String status;

    private String statusName;

    private Long reviewerId;

    private String reviewerName;

    private LocalDateTime reviewTime;

    private String reviewOpinion;

    private Long assigneeId;

    private String assigneeName;

    private LocalDateTime assignTime;

    private LocalDateTime acceptTime;

    private Integer acceptResult;

    private String acceptOpinion;

    private LocalDateTime archiveTime;

    private LocalDateTime createdAt;
}
