package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 候补通知历史。
 */
@Data
@Schema(description = "候补通知记录")
public class QueueNoticeVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long queueId;

    private Integer seq;

    private LocalDateTime noticeTime;

    private LocalDateTime deadline;

    @Schema(description = "通道：站内信+邮件")
    private String channelName;

    @Schema(description = "响应：未响应 / 已确认 / 已放弃 / 已超时")
    private String actionName;

    private Integer action;

    private LocalDateTime actionTime;
}
