package com.labreserve.rag.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * RAG 文档主表（{@code tb_rag_document}）。
 *
 * <p><b>时间戳</b>由数据库列默认值维护({@code DEFAULT CURRENT_TIMESTAMP} /
 * {@code ON UPDATE CURRENT_TIMESTAMP}),不依赖 MP 自动填充。
 *
 * <p><b>切片策略三层回退</b>:
 * <ul>
 *   <li>document.chunking_strategy / chunk_max_chars / chunk_overlap(本表字段)</li>
 *   <li>策略自带 defaultMaxChars / defaultOverlap(@link ChunkingStrategy)</li>
 *   <li>全局配置 lab-reserve.rag.ingest.chunk-*</li>
 * </ul>
 *
 * @see com.labreserve.rag.enums.RagPipelineState
 */
@Data
@TableName("tb_rag_document")
public class RagDocument implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    /** REGULATION / MANUAL / FAQ / SOP */
    private String category;

    /** PDF / DOCX / MD / TXT / PPT / XLSX */
    private String sourceType;

    /** OSS URL(完整 URL,EXTRACT 通过 OSS SDK 拉流) */
    private String fileUrl;

    private Integer charCount;
    private Integer chunkCount;

    /** 14 状态枚举的 {@code name()},见 {@link RagPipelineState} */
    private String pipelineState;

    /** 重建索引自增;旧 chunks / Qdrant points 异步清理 */
    private Integer ingestVersion;

    /** 文件 SHA-256,用于幂等(同 sha 上传返回已存在 ID) */
    private String contentSha256;

    private String lastErrorStep;
    private String lastErrorMsg;
    private LocalDateTime lastErrorAt;

    /** RECURSIVE / QA / FIXED_OVERLAP / SEMANTIC;NULL=走全局默认 */
    private String chunkingStrategy;

    /** NULL=走全局默认 */
    private Integer chunkMaxChars;
    private Integer chunkOverlap;

    private Long uploadedBy;

    /** 数据库 DEFAULT CURRENT_TIMESTAMP 维护 */
    private LocalDateTime createdAt;

    /** 数据库 ON UPDATE CURRENT_TIMESTAMP 维护 */
    private LocalDateTime updatedAt;
}
