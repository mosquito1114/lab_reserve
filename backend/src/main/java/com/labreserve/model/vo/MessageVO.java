package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 消息（收件箱列表 / 详情共用）。
 */
@Data
@Schema(description = "消息")
public class MessageVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    @Schema(description = "消息类型", example = "RESERVATION_AUDIT")
    private String type;

    @Schema(description = "消息类型中文名", example = "预约审核")
    private String typeName;

    private String title;

    @Schema(description = "正文（列表也会返回，便于前端做预览）")
    private String content;

    @Schema(description = "关联业务类型：RESERVATION / QUEUE / REPAIR_ORDER")
    private String bizType;

    @Schema(description = "关联业务 ID")
    private Long bizId;

    @Schema(description = "0 未读 1 已读")
    private Integer isRead;

    private LocalDateTime createdAt;
}
