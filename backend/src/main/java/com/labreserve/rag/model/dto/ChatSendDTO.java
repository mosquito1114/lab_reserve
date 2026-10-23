package com.labreserve.rag.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * RAG 提问入参（{@code POST /api/rag/chat/send}）。
 *
 * <p>{@code sessionId} 为空表示开新会话；非空则续该会话（必须属于当前用户）。
 */
@Data
@Schema(description = "RAG 提问入参")
public class ChatSendDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "会话 ID；留空 = 开新会话", example = "1")
    private Long sessionId;

    @Schema(description = "用户问题（≤4000 字，超长直接 400）", example = "化学实验室怎么取用易燃易爆品？",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请输入问题")
    @Size(max = 4000, message = "问题长度不能超过 4000 字")
    private String message;

    @Schema(description = "流式开关；true=SSE 逐字返回，false=一次性返回（默认 true）")
    private Boolean stream;
}