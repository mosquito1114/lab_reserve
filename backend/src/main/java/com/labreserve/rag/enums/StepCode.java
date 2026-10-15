package com.labreserve.rag.enums;

/**
 * RAG 摄取管道步骤编码。
 *
 * <p>R1 实现前三个(EXTRACT/SANITIZE/CHUNK),EMBED/UPSERT/FINALIZE 留给 R2。
 */
public enum StepCode {

    EXTRACT,
    SANITIZE,
    CHUNK,
    // R2 引入
    EMBED,
    UPSERT,
    FINALIZE
}
