package com.labreserve.rag.model.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 切片明细 VO（{@code GET /api/rag/admin/documents/{id}/chunks} 或 Step Result 内嵌）。
 *
 * <p>对应 {@code tb_rag_chunk}。{@code content} 是切片后原文（管理员视角，无需再脱敏）；前端
 * 引用追溯/手动核验时查这一行。
 *
 * <p>{@code vectorRef} 在 R2 EMBED/UPSERT 后才会被填入（Qdrant Point ID 形如 {@code "{docId}_{ver}_{idx}"}）。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "切片明细")
public class RagChunkVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "切片行主键")
    private Long id;

    @Schema(description = "所属文档 ID")
    private Long documentId;

    @Schema(description = "对应的摄取版本")
    private Integer ingestVersion;

    @Schema(description = "切片序号（从 0 开始；unique with document_id + ingest_version）")
    private Integer chunkIndex;

    @Schema(description = "切片正文")
    private String content;

    @Schema(description = "在原文中的字符起始偏移（含）")
    private Integer charStart;

    @Schema(description = "在原文中的字符终止偏移（不含）")
    private Integer charEnd;

    @Schema(description = "实际生效的切片策略 code")
    private String strategyCode;

    @Schema(description = "Qdrant Point ID（R2 起写入；R1 阶段为空）")
    private String vectorRef;

    private LocalDateTime createdAt;
}
