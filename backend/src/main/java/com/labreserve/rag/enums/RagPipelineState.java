package com.labreserve.rag.enums;

/**
 * RAG 文档摄取生命周期状态。
 *
 * <p>R1 阶段实际用到的状态只有前 6 个(CREATED → CHUNKED),
 * {@code EMBEDDING/EMBEDDED/UPSERTING/UPSERTED/READY} 留给 R2 补齐。
 *
 * <p>失败后转 {@link #FAILED} + 记 last_error_*;
 * 管理员可手动转 {@link #DISABLED}(从 READY 或 CHUNKED)。
 */
public enum RagPipelineState {

    CREATED,
    EXTRACTING, EXTRACTED,
    SANITIZING, SANITIZED,
    CHUNKING, CHUNKED,
    // R2 引入
    EMBEDDING, EMBEDDED,
    UPSERTING, UPSERTED,
    READY,
    DISABLED,
    FAILED
}
