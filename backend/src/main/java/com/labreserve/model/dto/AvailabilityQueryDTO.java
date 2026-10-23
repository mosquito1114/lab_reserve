package com.labreserve.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 资源可用性查询（{@code POST /api/laboratory/get_availability} 与
 * {@code POST /api/equipment/get_availability} 共用）。
 *
 * <p>实验室只关心日期；设备还可以只查指定时段（{@code slotIds} 为空表示全部启用时段）。
 */
@Data
@Schema(description = "资源可用性查询入参")
public class AvailabilityQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "资源 ID（实验室或设备）", example = "1")
    @NotNull(message = "资源 ID 不能为空")
    private Long id;

    @Schema(description = "日期", example = "2025-09-10")
    @NotNull(message = "日期不能为空")
    private LocalDate date;

    @Schema(description = "时段 ID 列表（仅设备用，为空表示全部启用时段）")
    private List<Long> slotIds;
}
