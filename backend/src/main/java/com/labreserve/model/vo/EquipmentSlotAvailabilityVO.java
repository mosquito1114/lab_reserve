package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalTime;

/**
 * 时段可用性（设备视角）。
 */
@Data
@Schema(description = "设备时段可用性")
public class EquipmentSlotAvailabilityVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long slotId;

    private String slotName;

    private LocalTime startTime;

    private LocalTime endTime;

    @Schema(description = "已占用数量")
    private Integer usedCount;

    @Schema(description = "剩余可借数量")
    private Integer remainCount;

    @Schema(description = "是否还能借")
    private Boolean available;

    @Schema(description = "当前排队人数")
    private Long queueCount;

    @Schema(description = "我在队列中的顺位（未排队为 null）")
    private Integer myQueuePosition;
}
