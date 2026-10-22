package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 设备某日的库存可用量。
 */
@Data
@Schema(description = "设备可用时段")
public class EquipmentAvailabilityVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long equipmentId;

    private String resourceNo;

    private String name;

    @Schema(description = "库存总量")
    private Integer stock;

    @Schema(description = "查询日期", example = "2025-09-10")
    private LocalDate date;

    @Schema(description = "各时段剩余库存")
    private List<EquipmentSlotAvailabilityVO> slots;
}
