package com.labreserve.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 设备报修工单（{@code tb_repair_order}）。
 *
 * <p>状态机（7 态）：
 * <pre>
 * SUBMITTED ──review 通过──> ASSIGNED ──维修人更新进度──> REPAIRING ──完成──> REPAIRED ──验收通过──> ACCEPTED
 *     │                          │                          ↑                              │
 *     └──review 驳回──> REJECTED  └────────── 派单（可重派）───┘                              └──验收不通过──> REOPENED ─┘
 * </pre>
 *
 * <p>需求 Q23：设备维修<b>不联动</b>作废任何预约，工单只是设备状态与维修过程的记录。
 */
@Data
@TableName("tb_repair_order")
public class RepairOrder implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String SUBMITTED = "SUBMITTED";
    public static final String REJECTED = "REJECTED";
    public static final String ASSIGNED = "ASSIGNED";
    public static final String REPAIRING = "REPAIRING";
    public static final String REPAIRED = "REPAIRED";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String REOPENED = "REOPENED";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工单号 WX-YYYYMMDD-XXXX */
    private String orderNo;

    /** 报修人 */
    private Long submitterId;

    /** 报修设备 */
    private Long equipmentId;

    /** 故障现象 */
    private String faultDesc;

    /** 发生场景 */
    private String faultScene;

    /** 故障截图 URL（OSS），逗号分隔 */
    private String faultImages;

    /** 1 低 / 2 中 / 3 高 / 4 紧急 */
    private Integer priority;

    private String status;

    private Long reviewerId;

    private LocalDateTime reviewTime;

    private String reviewOpinion;

    private Long assigneeId;

    private LocalDateTime assignTime;

    private Long acceptUserId;

    private LocalDateTime acceptTime;

    /** 1 通过 / 2 不通过 */
    private Integer acceptResult;

    private String acceptOpinion;

    private LocalDateTime archiveTime;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
