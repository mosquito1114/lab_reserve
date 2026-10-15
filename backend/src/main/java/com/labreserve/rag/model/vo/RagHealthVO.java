package com.labreserve.rag.model.vo;

import java.util.List;

/**
 * {@code POST /api/rag/health} 响应体。
 *
 * <p>各字段含义:
 * <ul>
 *   <li>{@code enabled} —— {@code lab-reserve.rag.enabled} 配置值</li>
 *   <li>{@code chatModelReady} —— Spring AI ChatModel Bean 是否装配成功(凭据是否齐)</li>
 *   <li>{@code qdrantReady} —— QdrantClient Bean 是否装配成功</li>
 *   <li>{@code defaultChunking} —— 默认切片方案</li>
 *   <li>{@code strategies} —— 已注册的所有切片策略(给前端 Upload UI 用)</li>
 * </ul>
 */
public record RagHealthVO(
        boolean enabled,
        boolean chatModelReady,
        boolean qdrantReady,
        String defaultChunking,
        List<StrategyItem> strategies
) {
    public record StrategyItem(
            String code,
            String name,
            String description,
            List<String> recommendedFor,
            int defaultMaxChars,
            int defaultOverlap
    ) {}
}
