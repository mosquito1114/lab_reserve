package com.labreserve.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 实时查询我的顺位（{@code POST /api/queue/get_my_position}）。
 */
@Data
@Schema(description = "我的顺位查询入参")
public class QueuePositionDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "资源类型不能为空")
    @Min(value = 1, message = "资源类型只能是 1 或 2")
    @Max(value = 2, message = "资源类型只能是 1 或 2")
    private Integer resourceType;

    private Long labId;

    private Long equipmentId;

    @NotNull(message = "预约日期不能为空")
    private LocalDate reserveDate;

    @NotNull(message = "时段不能为空")
    private Long slotId;
}
