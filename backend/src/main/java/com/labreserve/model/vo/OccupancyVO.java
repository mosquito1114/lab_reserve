package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 资源占用率（{@code api.md} §9.2 口径：已占用时段 / 开放时段）。
 */
@Data
@Schema(description = "资源占用率")
public class OccupancyVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "总体占用率（0~1）")
    private Double overall;

    private List<LabOccupancyVO> byLab;

    private List<EquipmentOccupancyVO> byEquipment;

    @Data
    @Schema(description = "实验室占用率")
    public static class LabOccupancyVO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private Long labId;
        private String labNo;
        private String labName;
        private Long openSlots;
        private Long usedSlots;
        private Double occupancyRate;
    }

    @Data
    @Schema(description = "设备占用率")
    public static class EquipmentOccupancyVO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private Long equipmentId;
        private String resourceNo;
        private String name;
        private Integer stock;
        private Long usedSlots;
        private Long totalSlots;
        private Double occupancyRate;
    }
}
