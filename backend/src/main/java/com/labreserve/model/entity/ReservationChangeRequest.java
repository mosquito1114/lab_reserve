package com.labreserve.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 预约调整申请（{@code tb_reservation_change_request}）。
 *
 * <p>核心约定：<b>审批通过前，预约单的业务字段一个都不动</b>，
 * 只把 {@code tb_reservation.status} 改成 {@code CHANGE_PENDING}；
 * 变更内容记在 {@code before_json} / {@code after_json} 里，审批通过时回写。
 */
@Data
@TableName("tb_reservation_change_request")
public class ReservationChangeRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 1 修改 / 2 取消 */
    public static final int TYPE_MODIFY = 1;
    public static final int TYPE_CANCEL = 2;

    /** 0 待审批 / 1 通过 / 2 驳回 */
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_APPROVED = 1;
    public static final int STATUS_REJECTED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reservationId;

    private Long applicantId;

    private Integer changeType;

    /** 变更前完整快照（JSON） */
    private String beforeJson;

    /** 变更后完整快照（JSON，取消时与变更前一致） */
    private String afterJson;

    private String reason;

    private Integer status;

    private Long auditUserId;

    private LocalDateTime auditTime;

    private String auditOpinion;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
