package com.labreserve.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 我的禁入记录查询（{@code POST /api/queue_ban/get_my_list}）。
 */
@Data
@Schema(description = "我的禁入记录查询入参")
public class QueueBanQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "是否只看生效中的", example = "true")
    private Boolean activeOnly;
}
