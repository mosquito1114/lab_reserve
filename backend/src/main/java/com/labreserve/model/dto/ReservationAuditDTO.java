package com.labreserve.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 预约审核（{@code POST /api/reservation/audit}）。
 */
@Data
@Schema(description = "预约审核入参")
public class ReservationAuditDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "预约 ID 不能为空")
    private Long id;

    @Schema(description = "审核结果：APPROVED / REJECTED", example = "APPROVED")
    @NotBlank(message = "审核结果不能为空")
    @Pattern(regexp = "^(APPROVED|REJECTED)$", message = "审核结果只能是 APPROVED 或 REJECTED")
    private String result;

    @Schema(description = "审核意见（驳回时必填）", example = "该时段已安排其他课程")
    @Size(max = 255, message = "审核意见不能超过 255 个字符")
    private String opinion;
}
