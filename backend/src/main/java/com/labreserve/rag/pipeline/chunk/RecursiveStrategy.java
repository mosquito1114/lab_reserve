package com.labreserve.rag.pipeline.chunk;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * RECURSIVE 切片策略(默认)。
 *
 * <p>核心算法(待 R1 实现): <b>多级分隔符优先级递归</b>
 * <ol>
 *   <li>第一优先级: {@code \n\n} 段落切</li>
 *   <li>第二优先级: {@code \n} 行切</li>
 *   <li>第三优先级: 中文句号/问号/感叹号 + 空白</li>
 *   <li>第四优先级: 空格</li>
 *   <li>兜底: 纯字符定长</li>
 * </ol>
 *
 * <p>每片带 {@code overlap} 字符重叠,避免检索时切在语义边界。
 *
 * <p>R0 阶段留空实现,只占 {@code @Component} 标 R1 TODO,
 * 让 {@link ChunkingStrategyRegistry} 能正常注册。
 *
 * @see ChunkingStrategy
 */
@Slf4j
@Component
public class RecursiveStrategy implements ChunkingStrategy {

    @Override
    public String code() { return "RECURSIVE"; }

    @Override
    public String name() { return "递归分块（默认）"; }

    @Override
    public String description() { return "段落优先递归切分（多级分隔符），过阈值再切分；带 overlap。" +
            "适合大多数 MD / DOCX / 制度类文档。"; }

    @Override
    public List<String> recommendedFor() {
        return List.of("REGULATION", "SOP", "MANUAL", "DEFAULT");
    }

    @Override
    public int defaultMaxChars() { return 500; }

    @Override
    public int defaultOverlap() { return 100; }

    @Override
    public List<RawChunk> split(String cleanText, SplitContext ctx) {
        // TODO R1: 实现多级分隔符递归 + overlap
        log.warn("[RAG][RECURSIVE] R1 阶段实现; 现返回单 chunk 占位");
        return List.of(RawChunk.of(cleanText, 0, cleanText.length()));
    }
}
