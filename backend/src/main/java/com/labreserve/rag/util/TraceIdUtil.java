package com.labreserve.rag.util;

import java.util.UUID;

/**
 * RAG 链路 traceId 工具（无状态，故用静态方法）。
 *
 * <p>格式：{@code "rag-" + 32 位 hex}，与摄取链的 {@code TraceContext} 同格式，
 * 便于在 {@code tb_rag_pipeline_step.trace_id} 与 {@code tb_rag_message.trace_id} 之间串联。
 */
public final class TraceIdUtil {

    private TraceIdUtil() {
    }

    public static String newId() {
        return "rag-" + UUID.randomUUID().toString().replace("-", "");
    }
}