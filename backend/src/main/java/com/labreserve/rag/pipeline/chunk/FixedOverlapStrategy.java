package com.labreserve.rag.pipeline.chunk;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * FIXED_OVERLAP 切片策略（design.md §11.3 第 11 项 / 兜底）。
 *
 * <p>最朴素的滑动窗口：固定 {@code maxChars} 切一刀，下一刀起点往前回退 {@code overlap} 字符。
 * <b>不感知任何语义边界</b>，适合扫描 PDF / 纯文本这种没有段落结构的素材。
 *
 * <p>为什么还要有这条兜底？RecursiveStrategy 在退化到字符级切割前有时仍会有很长的
 * 单字符段（极端 OCR 文本），用 FixedOverlap 重切可重整成均匀块给 Embedding。
 *
 * <p>推荐场景：{@code PDF_SCAN}、OCR 后的纯文本、无段落结构材料。
 */
@Slf4j
@Component
public class FixedOverlapStrategy implements ChunkingStrategy {

    @Override
    public String code() { return "FIXED_OVERLAP"; }

    @Override
    public String name() { return "重叠分块（兜底）"; }

    @Override
    public String description() {
        return "按固定字数切，带 overlap；不感知语义。"
                + "适合无段落结构的扫描 PDF / 纯文本。";
    }

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
        if (cleanText == null || cleanText.isEmpty()) {
            return List.of();
        }
        String text = ChunkNormalizer.nfc(cleanText).trim();
        if (text.isEmpty()) {
            return List.of();
        }
        int maxChars = Math.max(1, ctx.getMaxChars());
        int overlap = Math.max(0, Math.min(overlapSafeOverlap(ctx.getOverlap()), maxChars - 1));
        int len = text.length();

        // 没有 overlap 时直接等分
        if (overlap <= 0) {
            List<RawChunk> result = new ArrayList<>();
            int pos = 0;
            while (pos < len) {
                int end = Math.min(pos + maxChars, len);
                result.add(RawChunk.of(text.substring(pos, end), pos, end));
                pos = end;
            }
            log.debug("[RAG][FIXED_OVERLAP] 无 overlap: 总长={} maxChars={} chunks={}",
                    len, maxChars, result.size());
            return result;
        }

        // 带 overlap 的滑窗
        List<RawChunk> result = new ArrayList<>();
        int pos = 0;
        while (pos < len) {
            int end = Math.min(pos + maxChars, len);
            result.add(RawChunk.of(text.substring(pos, end), pos, end));
            if (end >= len) {
                break;
            }
            // 下次起点 = 这次终点 - overlap；但绝不能 ≤ 当前起点（否则死循环）
            int nextPos = end - overlap;
            if (nextPos <= pos) {
                nextPos = pos + 1;
            }
            pos = nextPos;
        }
        log.debug("[RAG][FIXED_OVERLAP] overlap={}: 总长={} maxChars={} chunks={}",
                overlap, len, maxChars, result.size());
        return result;
    }

    /** overlap 不能为负；这里返回 0 表示"无 overlap"语义，与 SplitContext.getOverlap() 兼容 */
    private static int overlapSafeOverlap(Integer overlap) {
        return overlap == null ? 0 : overlap;
    }
}
