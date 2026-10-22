package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalTime;

/**
 * 时段可用性（实验室视角）。
 */
@Data
@Schema(description = "时段可用性")
public class SlotAvailabilityVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long slotId;

    private String slotName;

    private LocalTime startTime;

    private LocalTime endTime;

    @Schema(description = "是否可预约")
    private Boolean available;

    @Schema(description = "不可预约原因；可预约时为 null", example = "已被预约")
    private String reason;

    @Schema(description = "当前排队人数")
    private Long queueCount;

    @Schema(description = "我在队列中的顺位（未排队为 null）")
    private Integer myQueuePosition;
}
