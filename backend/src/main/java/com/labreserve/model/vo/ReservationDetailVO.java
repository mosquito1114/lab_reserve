package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 预约详情（含附带设备、审核信息与待审批的调整申请）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "预约详情")
public class ReservationDetailVO extends ReservationVO {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "附带设备清单（实验室顺带借的）")
    private List<ReservationEquipmentVO> equipments;

    private Long auditUserId;

    @Schema(description = "审核人姓名")
    private String auditUserName;

    private LocalDateTime auditTime;

    private String rejectReason;

    @Schema(description = "是否由排队转正：0 否 / 1 是")
    private Integer fromQueue;

    @Schema(description = "正在审批中的调整申请（没有则为 null）")
    private ReservationChangeVO changePending;
}
