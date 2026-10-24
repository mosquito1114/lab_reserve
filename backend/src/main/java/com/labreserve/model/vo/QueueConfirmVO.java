package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 候补确认结果（直接生成 APPROVED 预约单）。
 */
@Data
@Schema(description = "候补确认结果")
public class QueueConfirmVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long reservationId;

    private String reserveNo;

    @Schema(description = "预约状态，固定 APPROVED", example = "APPROVED")
    private String status;

    private String message;
}
