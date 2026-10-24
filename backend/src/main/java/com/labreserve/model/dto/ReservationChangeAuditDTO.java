package com.labreserve.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 审批调整申请（{@code POST /api/reservation_change/audit}）。
 */
@Data
@Schema(description = "调整审批入参")
public class ReservationChangeAuditDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "调整申请 ID 不能为空")
    private Long id;

    @Schema(description = "审批结果：1 通过 / 2 驳回", example = "1")
    @NotNull(message = "审批结果不能为空")
    @Min(value = 1, message = "审批结果只能是 1 或 2")
    @Max(value = 2, message = "审批结果只能是 1 或 2")
    private Integer result;

    @Schema(description = "审批意见", example = "同意调整")
    @Size(max = 255, message = "审批意见不能超过 255 个字符")
    private String opinion;
}
