package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 实时顺位（轻量接口，前端轮询用）。
 */
@Data
@Schema(description = "我的实时顺位")
public class QueuePositionVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "是否在队列中")
    private Boolean queued;

    private Long queueId;

    private String queueNo;

    @Schema(description = "顺位（1 表示下一个）")
    private Integer position;

    private Long totalCount;

    @Schema(description = "状态：QUEUING / NOTIFIED / ...")
    private String status;

    private Long remainSeconds;
}
