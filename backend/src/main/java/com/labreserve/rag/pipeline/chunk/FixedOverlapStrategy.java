package com.labreserve.rag.pipeline.chunk;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * FIXED_OVERLAP 切片策略(兜底 —— 无段落结构的纯文本/扫描 PDF)。
 *
 * <p>核心算法(待 R1 实现): 严格按 {@code maxChars} 切窗,起点 = 上一切点 + maxChars - overlap,
 * 切到末位时收集剩余不超 {@code maxChars} 的尾巴。
 *
 * <p>R0 阶段留空实现,只占 {@code @Component} 标 R1 TODO。
 */
@Slf4j
@Component
public class FixedOverlapStrategy implements ChunkingStrategy {

    @Override
    public String code() { return "FIXED_OVERLAP"; }

    @Override
    public String name() { return "重叠分块（兜底）"; }

    @Override
    public String description() { return "按固定字数切,带 overlap；不感知语义。" +
            "适合无段落结构的扫描 PDF / 纯文本。"; }

    @Override
    public List<String> recommendedFor() {
        return List.of("PDF_SCAN");
    }

    @Override
    public int defaultMaxChars() { return 500; }

    @Override
    public int defaultOverlap() { return 100; }

    @Override
    public List<RawChunk> split(String cleanText, SplitContext ctx) {
        // TODO R1: 实现滑动窗口
        log.warn("[RAG][FIXED_OVERLAP] R1 阶段实现; 现返回单 chunk 占位");
        return List.of(RawChunk.of(cleanText, 0, cleanText.length()));
    }
}
