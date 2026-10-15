package com.labreserve.rag.pipeline.chunk;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * QA 问答对切片策略(用于 FAQ 类文档)。
 *
 * <p>核心算法(待 R1 实现): 识别 4-5 种模式(权重合并):
 * <ul>
 *   <li>{@code ^Q[\.:：]\\s} / {@code ^问题\\d*[:：]?} / {@code ^第[一二三四五六七八九十百]问}</li>
 *   <li>{@code ^A[\.:：]\\s} / {@code ^答案[:：]?}</li>
 * </ul>
 *
 * <p>每个 Q+A 对一个 chunk;QA 不重叠({@code overlap=0})。
 *
 * <p>R0 阶段留空实现,只占 {@code @Component} 标 R1 TODO。
 */
@Slf4j
@Component
public class QaStrategy implements ChunkingStrategy {

    @Override
    public String code() { return "QA"; }

    @Override
    public String name() { return "问答对分块"; }

    @Override
    public String description() { return "识别 Q / A / 问题 / 答案 等行首模式,每对 Q+A 切为一片。" +
            "适合 FAQ 类文档;默认不重叠。"; }

    @Override
    public List<String> recommendedFor() {
        return List.of("FAQ");
    }

    @Override
    public int defaultMaxChars() { return 2000; }

    @Override
    public int defaultOverlap() { return 0; }

    @Override
    public List<RawChunk> split(String cleanText, SplitContext ctx) {
        // TODO R1: 实现 Q&A 模式匹配 + 配对
        log.warn("[RAG][QA] R1 阶段实现; 现返回单 chunk 占位");
        return List.of(RawChunk.of(cleanText, 0, cleanText.length()));
    }

    /** 正则占位,实际 R1 写完整 */
    private static final Pattern Q_PREFIX = Pattern.compile("^Q[\\.\\:：]\\s.*|^问题\\d*[:：]?\\s.*");
}
