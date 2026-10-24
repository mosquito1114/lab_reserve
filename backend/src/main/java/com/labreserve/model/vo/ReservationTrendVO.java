package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 预约趋势（折线图）。
 */
@Data
@Schema(description = "预约趋势")
public class ReservationTrendVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String groupBy;

    private List<Item> items;

    @Data
    @Schema(description = "趋势项")
    public static class Item implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(description = "周期：2026-10-24 / 2026-W43 / 2026-10", example = "2026-10")
        private String period;
        private Long total;
        private Long approved;
        private Long rejected;
        private Long canceled;
    }
}
