package com.labreserve.rag.pipeline;

import lombok.Builder;

/**
 * 单步执行上下文（{@link AbstractPipelineStep#execute(StepContext)} 的入参）。
 *
 * <p>每跑一步构建一次；同一步的"全链重跑"和"手动重试"共用同一个 record 形态，
 * 但 RAG 设计上<b>不</b>在这两个上下文之间复用（自动调度器与手动控制器独立生成）。
 *
 * <p>字段足够让业务步骤找到它需要的资料 + 串起 traceId：
 * <ul>
 *   <li>{@code documentId} / {@code ingestVersion} —— 主要定位键</li>
 *   <li>{@code traceId} —— 跨 Step / 跨落库关联</li>
 *   <li>{@code retryAttempt} —— 第几次重试（0 = 首次执行；Spring Retry 上限后仍失败则让 FAILED）</li>
 *   <li>{@code sha256} —— 文档指纹（manual 时由 admin 传入，与 tb_rag_document.content_sha256 对照）</li>
 * </ul>
 *
 * <p>为什么 {@code retryAttempt} 默认为 0？
 * {@link Builder} 默认值由 lombok 生成，{@code retryAttempt(0)} 与 {@code sha256(null)} 是合理默认。
 */
@Builder
public record StepContext(
        Long documentId,
        Integer ingestVersion,
        String traceId,
        Integer retryAttempt,
        String sha256
) {
    /**
     * 默认构造：从 document/version/traceId 起步（自动派发路径常用）。
     */
    public static StepContext of(Long docId, Integer version, String traceId) {
        return StepContext.builder()
                .documentId(docId)
                .ingestVersion(version)
                .traceId(traceId)
                .retryAttempt(0)
                .build();
    }
}
