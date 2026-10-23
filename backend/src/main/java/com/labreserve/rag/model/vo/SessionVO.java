package com.labreserve.rag.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会话列表条目（{@code POST /api/rag/chat/sessions} 的返回项）。
 */
@Data
@Schema(description = "会话列表条目")
public class SessionVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "会话 ID")
    private Long id;

    @Schema(description = "会话标题")
    private String title;

    @Schema(description = "消息数")
    private Integer messageCount;

    @Schema(description = "最后活跃时间")
    private LocalDateTime lastMessageAt;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}