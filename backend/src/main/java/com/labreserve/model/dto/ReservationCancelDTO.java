package com.labreserve.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 撤销 / 释放预约（{@code cancel} 与 {@code release} 共用）。
 */
@Data
@Schema(description = "预约撤销 / 释放入参")
public class ReservationCancelDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "预约 ID 不能为空")
    private Long id;

    @Schema(description = "原因", example = "课程取消")
    @Size(max = 255, message = "原因不能超过 255 个字符")
    private String reason;
}
