package com.labreserve.rag.model.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 文档步骤时间线 VO（{@code GET /api/rag/admin/documents/{id}/timeline}）。
 *
 * <p>用途：前端可视化"DAG"——每一行是一次 Step 执行（包含 RUNNING/SUCCESS/FAILED/SKIPPED），
 * 状态码语义：1 RUNNING / 2 SUCCESS / 3 FAILED / 4 SKIPPED（与 {@code tb_rag_pipeline_step.status} 字段一致）。
 *
 * <p>{@code outputMeta} 是 JSON 字符串（R1：策略、maxChars、overlap、chunkCount、avgChars、maxChunkChars，
 * 等）。前端按需 JSON.parse 反序列化。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "文档步骤时间线（按 ingestVersion 与时间正序）")
public class RagStepTimelineVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "文档 ID")
    private Long documentId;

    @Schema(description = "对应的摄取版本")
    private Integer ingestVersion;

    @Schema(description = "当前状态机")
    private String pipelineState;

    @Schema(description = "步骤列表（按 startedAt 升序；RUNNING 排最前）")
    private List<StepEntry> steps;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "单次步骤执行")
    public static class StepEntry implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(description = "步骤行主键")
        private Long id;

        @Schema(description = "步骤编码 EXTRACT/SANITIZE/CHUNK/EMBED/UPSERT/FINALIZE")
        private String stepCode;

        @Schema(description = "状态 1 RUNNING / 2 SUCCESS / 3 FAILED / 4 SKIPPED")
        private Integer status;

        @Schema(description = "入参 sha256（可空：EXTRACT 的入参为文件 URL 不算 sha）")
        private String inputSha256;

        @Schema(description = "出参 sha256（可空：RUNNING/FAILED 阶段无）")
        private String outputSha256;

        @Schema(description = "出参统计 JSON：{strategy, maxChars, overlap, chunkCount, tokens, points, ...}")
        private String outputMeta;

        @Schema(description = "traceId（与 tb_rag_message.trace_id 关联排查）")
        private String traceId;

        @Schema(description = "耗时毫秒")
        private Integer costMs;

        @Schema(description = "失败消息（SUCCESS 时为空）")
        private String errorMsg;

        @Schema(description = "开始时间")
        private LocalDateTime startedAt;

        @Schema(description = "结束时间")
        private LocalDateTime finishedAt;
    }
}
