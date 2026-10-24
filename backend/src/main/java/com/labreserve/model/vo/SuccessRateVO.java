package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 预约成功率（APPROVED / 提交总数）。
 */
@Data
@Schema(description = "预约成功率")
public class SuccessRateVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long total;
    private Long approved;
    private Long rejected;
    private Long canceled;
    private Long fromQueue;
    @Schema(description = "成功率（0~1）")
    private Double successRate;
    private Double rejectRate;
    private Double cancelRate;
}
