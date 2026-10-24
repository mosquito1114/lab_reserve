package com.labreserve.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 放弃候补（{@code POST /api/queue/abandon}）。
 */
@Data
@Schema(description = "放弃候补入参")
public class QueueAbandonDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "排队记录 ID 不能为空")
    private Long id;

    @Schema(description = "放弃原因", example = "临时有事")
    @Size(max = 255, message = "原因不能超过 255 个字符")
    private String reason;
}
