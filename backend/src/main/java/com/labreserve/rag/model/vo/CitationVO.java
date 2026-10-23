package com.labreserve.rag.model.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 引用条目（{@link RagStreamItem#citations} 里的一项）。
 *
 * <p>对应 {@code tb_rag_chunk} 的一行——用 {@code chunkId} / {@code vectorRef}
 * 就能反查 Qdrant point 与原始切片，保证"引用可追溯"。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "引用条目")
public class CitationVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "引用序号（对应答案里的 [1] [2] 角标，从 1 开始）")
    private Integer index;

    @Schema(description = "来源文档 ID")
    private Long documentId;

    @Schema(description = "来源文档标题")
    private String title;

    @Schema(description = "来源文档类别（REGULATION / MANUAL / FAQ / SOP）")
    private String category;

    @Schema(description = "来源切片 ID（可追溯到 tb_rag_chunk 行）")
    private Long chunkId;

    @Schema(description = "Qdrant Point ID（doc-{id}-v{ver}-{idx}）")
    private String vectorRef;

    @Schema(description = "引用片段原文（截断到 200 字）")
    private String snippet;

    @Schema(description = "相似度分数（RRF 融合后或 rerank 后的最终分）")
    private Double score;
}