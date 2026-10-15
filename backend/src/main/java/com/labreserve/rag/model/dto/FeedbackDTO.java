package com.labreserve.rag.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * RAG 消息反馈入参（{@code POST /api/rag/chat/message/{id}/feedback}）。
 *
 * <p>R3 启用（设计图 §4.9 评估门禁）。R1 先把 DTO 定下，便于前端按需禁用且不影响接口文档。
 *
 * <p>{@link #feedback} 语义：
 * <ul>
 *   <li>{@code 1} —— 有用（后续训练语料/正面 RLHF）</li>
 *   <li>{@code 0} —— 没用（用于评估门禁的负反馈集）</li>
 * </ul>
 */
@Data
@Schema(description = "RAG 消息反馈入参（R3+）")
public class FeedbackDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "被评价的消息 ID（tb_rag_message.id）", example = "67890")
    @NotNull(message = "消息 ID 不能为空")
    private Long messageId;

    @Schema(description = "1 有用 / 0 没用", example = "1", allowableValues = {"0", "1"})
    @NotNull(message = "反馈值不能为空")
    @Min(value = 0, message = "feedback 仅支持 0 或 1")
    @Max(value = 1, message = "feedback 仅支持 0 或 1")
    private Integer feedback;
}
