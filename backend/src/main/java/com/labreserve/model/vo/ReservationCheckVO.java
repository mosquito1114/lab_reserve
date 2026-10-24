package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 预检结果（{@code POST /api/reservation/check}）。
 *
 * <p>前端据此决定：直接提交、弹「已被预约，是否排队」、还是提示禁入中。
 */
@Data
@Schema(description = "预约预检结果")
public class ReservationCheckVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "是否可提交")
    private Boolean available;

    @Schema(description = "冲突信息；可预约时为 null")
    private ConflictVO conflict;

    @Schema(description = "是否允许加入排队（迭代 6 启用，本轮仅作提示依据）")
    private Boolean canQueue;

    @Schema(description = "禁入信息；未禁入时为 null")
    private BanInfoVO banInfo;

    @Data
    @Schema(description = "冲突信息")
    public static class ConflictVO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(description = "冲突码", example = "SLOT_OCCUPIED")
        private String code;

        @Schema(description = "提示文案", example = "该时段已被预约")
        private String message;

        @Schema(description = "当前排队人数")
        private Long queueCount;

        @Schema(description = "我在队列中的顺位")
        private Integer myQueuePosition;
    }

    @Data
    @Schema(description = "禁入信息")
    public static class BanInfoVO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private LocalDateTime startTime;

        private LocalDateTime endTime;

        private String reason;
    }
}
