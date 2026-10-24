package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 排队禁入记录。
 */
@Data
@Schema(description = "排队禁入记录")
public class QueueBanVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Integer resourceType;

    @Schema(description = "资源名称", example = "LAB-01 计算机基础实验室")
    private String resourceName;

    private String reason;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    @Schema(description = "是否仍在禁入期")
    private Boolean active;
}
