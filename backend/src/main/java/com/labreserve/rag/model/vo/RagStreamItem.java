package com.labreserve.rag.model.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * SSE 流式事件（{@code POST /api/rag/chat/send} 的 data 负载）。
 *
 * <p>事件类型见 {@link Kind}。前端按 {@code kind} 分支处理：
 * <pre>
 * chunk     → 追加到当前气泡（delta 是增量文本）
 * citation  → 渲染引用角标 [n]
 * message   → 拿到最终 messageId（可提交反馈）
 * degraded  → 展示"本次走了降级路径"提示（不影响已收到的正文）
 * done      → 流结束，前端关闭 EventSource
 * error     → 流异常，前端展示错误并关闭
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "RAG SSE 流式事件")
public class RagStreamItem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** SSE 事件类型 */
    public enum Kind {
        /** 正文增量 */
        CHUNK,
        /** 引用（正文全部吐完后一次性发） */
        CITATION,
        /** 结束（携带最终 messageId + token 统计） */
        MESSAGE,
        /** 走了降级路径（LLM 超时 / 限流等），携带降级原因 */
        DEGRADED,
        /** 正常结束 */
        DONE,
        /** 异常结束 */
        ERROR
    }

    @Schema(description = "事件类型")
    private Kind kind;

    @Schema(description = "正文增量（仅 CHUNK）")
    private String delta;

    @Schema(description = "引用列表（仅 CITATION / MESSAGE）")
    private List<CitationVO> citations;

    @Schema(description = "最终消息 ID（仅 MESSAGE）")
    private Long messageId;

    @Schema(description = "本次消耗 token（仅 MESSAGE）")
    private Integer totalTokens;

    @Schema(description = "端到端耗时 ms（仅 MESSAGE）")
    private Integer costMs;

    @Schema(description = "降级原因（仅 DEGRADED）")
    private String reason;

    @Schema(description = "错误信息（仅 ERROR）")
    private String error;
}