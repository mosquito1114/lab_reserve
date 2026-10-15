package com.labreserve.rag.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * RAG 切片明细（{@code tb_rag_chunk}）。
 *
 * <p>每片一行;uk 为 {@code (documentId, ingestVersion, chunkIndex)}。
 * {@code vectorRef} 由 R2 起写入 Qdrant Point ID（{@code {docId}_{ver}_{idx}}）。
 */
@Data
@TableName("tb_rag_chunk")
public class RagChunk implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long documentId;
    private Integer ingestVersion;
    private Integer chunkIndex;

    private String content;
    private Integer charStart;
    private Integer charEnd;

    /** RECURSIVE / QA / FIXED_OVERLAP / SEMANTIC */
    private String strategyCode;

    /** Qdrant Point ID（R2 起写入） */
    private String vectorRef;

    /** EMBED Step 写入：raw float[vector-size] little-endian，
     *  让 UPSERT 重跑可省 LLM 调用；R2 起写入 */
    private byte[] vectorBlob;

    /** 数据库 DEFAULT CURRENT_TIMESTAMP 维护 */
    private LocalDateTime createdAt;
}
