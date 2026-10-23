package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 邮件投递记录（管理员排查用）。
 */
@Data
@Schema(description = "邮件投递记录")
public class MessageDeliveryLogVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long messageId;

    private Long receiverId;

    @Schema(description = "收件人姓名")
    private String receiverName;

    @Schema(description = "收件人账号")
    private String receiverUsername;

    @Schema(description = "收件邮箱（脱敏）")
    private String receiverEmail;

    private String title;

    @Schema(description = "SENDING 投递中 / OK 成功 / DEAD 死信")
    private String emailState;

    @Schema(description = "最后一次失败原因")
    private String emailLastError;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
