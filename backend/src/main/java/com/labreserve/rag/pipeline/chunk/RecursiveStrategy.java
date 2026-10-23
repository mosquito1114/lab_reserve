package com.labreserve.rag.pipeline.chunk;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * RECURSIVE 切片策略（默认，design.md §11.3 第 9 项）。
 *
 * <p><b>多级分隔符优先级递归</b>：
 * <ol>
 *   <li>{@code "\n\n"} —— 段落切</li>
 *   <li>{@code "\n"} —— 行切</li>
 *   <li>{@code "。"} —— 句号</li>
 *   <li>{@code "！"} —— 感叹号</li>
 *   <li>{@code "？"} —— 问号</li>
 *   <li>{@code "；"} —— 分号</li>
 *   <li>{@code "，"} —— 逗号</li>
 *   <li>{@code " "} —— 空格</li>
 *   <li><b>退路</b>：单字符定长</li>
 * </ol>
 *
 * <p>每个切长阶段贪心合并相邻片段到不超过 maxChars，超出的部分递归进下一级；
 * 一直退到单字符兜底仍可能存在超长片（极少见），由 {@link ChunkNormalizer} 长二分补救。
 *
 * <p>overlap：相邻 chunk 尾巴追加下个 chunk 的头部至多 {@code overlap} 字符；
 * 此时 {@code charStart} 取前一片的 start，{@code charEnd} 推到 overlap 段的结尾。
 *
 * <p>推荐场景：{@code REGULATION / SOP / MANUAL}（默认走这条）。
 */
@Slf4j
@Component
public class RecursiveStrategy implements ChunkingStrategy {

    /** 分隔符优先级列表（最粗→最细）。保留顺序：删减可能让中文文档断句错误。 */
    private static final String[] SEPARATORS = {
            "\n\n", "\n", "。", "！", "？", "；", "，", " ", ""
    };

    @Override
    public String code() { return "RECURSIVE"; }

    @Override
    public String name() { return "递归分块（默认）"; }

    @Override
    public String description() {
        return "段落优先递归切分（多级分隔符），过阈值再切分；带 overlap。"
                + "适合大多数 MD / DOCX / 制度类文档。";
    }

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
        if (cleanText == null || cleanText.isEmpty()) {
            return List.of();
        }
        // 进入切片前先 NFC 归一化（design.md §11.3 第 11 项）
        String text = ChunkNormalizer.nfc(cleanText).trim();
        if (text.isEmpty()) {
            return List.of();
        }
        int maxChars = Math.max(1, ctx.getMaxChars());
        int overlap = Math.max(0, ctx.getOverlap());

        List<RawChunk> chunks = new ArrayList<>();
        splitRecursive(text, 0, text.length(), 0, maxChars, chunks);

        // 短合并 + 长二分
        chunks = ChunkNormalizer.normalize(chunks, maxChars);

        // overlap：每片末尾追加下片头
        if (overlap > 0 && chunks.size() > 1) {
            chunks = applyOverlap(chunks, text, overlap);
        }

        log.debug("[RAG][RECURSIVE] 切片完成: 总长={} maxChars={} overlap={} chunks={}",
                text.length(), maxChars, overlap, chunks.size());
        return chunks;
    }

    // --------------------------------------------------------------
    // 递归拆分
    // --------------------------------------------------------------

    /**
     * 递归：把 {@code [from, to)} 区间塞进 maxChars；不能塞时按 {@code SEPARATORS[sepIdx]} 切。
     *
     * @param text     整段清洗过的正文
     * @param from     当前区间起始（字符偏移，含）
     * @param to       当前区间结束（不含）
     * @param sepIdx   {@link #SEPARATORS} 当前层下标
     * @param maxChars 切长上限
     * @param out      结果累加器
     */
    private void splitRecursive(String text, int from, int to, int sepIdx, int maxChars, List<RawChunk> out) {
        int len = to - from;
        if (len <= 0) return;

        if (len <= maxChars) {
            out.add(RawChunk.of(text.substring(from, to), from, to));
            return;
        }
        if (sepIdx >= SEPARATORS.length) {
            // 已经走到 SEPARATORS 末尾还没拆完 —— 不可能发生（最后一级是空字符串强切）
            return;
        }

        String sep = SEPARATORS[sepIdx];
        if (sep.isEmpty()) {
            // 最后退路：单字符定长窗
            int pos = from;
            while (pos < to) {
                int end = Math.min(pos + maxChars, to);
                out.add(RawChunk.of(text.substring(pos, end), pos, end));
                pos = end;
            }
            return;
        }

        // 用 sep 找出 [from, to) 内所有分隔点；按分隔符切出一组 (start, end) 段
        List<int[]> parts = splitBySep(text, from, to, sep);
        if (parts.size() <= 1) {
            // 没有 sep（极少见，如整段没有段落符）；降级到下一级
            splitRecursive(text, from, to, sepIdx + 1, maxChars, out);
            return;
        }

        // 把相邻 parts 合并到不超过 maxChars 的窗口 → 每个窗口再向下递归
        int windowStart = -1;
        int windowEnd = -1;
        for (int[] part : parts) {
            int pStart = part[0];
            int pEnd = part[1];
            if (pEnd == pStart) {
                continue; // 跳过分隔产生的空段
            }
            if (windowStart < 0) {
                windowStart = pStart;
                windowEnd = pEnd;
            } else if (pEnd - windowStart <= maxChars) {
                // 累加还在范围内
                windowEnd = pEnd;
            } else {
                // 已超，递归先消化当前 window
                splitRecursive(text, windowStart, windowEnd, sepIdx + 1, maxChars, out);
                windowStart = pStart;
                windowEnd = pEnd;
            }
        }
        if (windowStart >= 0) {
            splitRecursive(text, windowStart, windowEnd, sepIdx + 1, maxChars, out);
        }
    }

    /**
     * 把 {@code text[from..to)} 按 {@code sep} 切成片段，分隔符保留在前一片的尾。
     * 例：{@code "AB\n\nCD"} 按 {@code "\n\n"} 切 → {@code [(A,B), (C,D), (,D)} 取首尾，
     * 这里我们采用 <b>不含分隔符</b> 的语义：{@code "AB\n\nCD"} → {@code [(0,2), (4,6)]}。
     */
    private List<int[]> splitBySep(String text, int from, int to, String sep) {
        List<int[]> parts = new ArrayList<>();
        int pos = from;
        while (pos < to) {
            int sepIdx = text.indexOf(sep, pos);
            if (sepIdx < 0 || sepIdx >= to) {
                // 最后一段
                if (pos < to) {
                    parts.add(new int[]{pos, to});
                }
                break;
            }
            if (sepIdx > pos) {
                parts.add(new int[]{pos, sepIdx});
            }
            pos = sepIdx + sep.length();
        }
        return parts;
    }

    // --------------------------------------------------------------
    // overlap：在每片尾部追加下片头
    // --------------------------------------------------------------

    /**
     * 给每片（除最后一片）尾部追加下片的字符前缀（最多 {@code overlap} 字符），
     * 同步把 {@link RawChunk#charEnd()} 推到 overlap 段的尾部。
     *
     * <p>注意：实现用了"扩展下一片的起点"方式而不是"扩展本片的结尾"——
     * 这种做法会让一片的结束与下片的开始重叠，下一片的 start 在原位置不变，
     * 相当于把<b>重叠区域</b>同时归到两片内。检索语义上更鲁棒。
     */
    private List<RawChunk> applyOverlap(List<RawChunk> chunks, String text, int overlap) {
        // 先只追加 content；charStart / charEnd 在下一步统一调整
        List<RawChunk> extended = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size() - 1; i++) {
            RawChunk cur = chunks.get(i);
            RawChunk next = chunks.get(i + 1);
            int overlapEnd = Math.min(next.charStart() + overlap, text.length());
            if (overlapEnd <= cur.charStart()) {
                extended.add(cur);
                continue;
            }
            String suffix = text.substring(next.charStart(), overlapEnd);
            String newContent = cur.content() + suffix;
            extended.add(RawChunk.of(newContent, cur.charStart(), overlapEnd));
        }
        extended.add(chunks.get(chunks.size() - 1));
        return extended;
    }
}
