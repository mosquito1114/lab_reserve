package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 未读消息数（顶栏铃铛角标）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "未读消息数")
public class UnreadCountVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "未读条数", example = "5")
    private Long count;
}
