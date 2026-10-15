package com.labreserve.rag.pipeline.chunk;

import lombok.extern.slf4j.Slf4j;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/**
 * 切片后处理（design.md §3.4 / §11.3 第 9 项）。
 *
 * <p>三个策略切完后统一过两个规则：
 * <ol>
 *   <li><b>短合并</b>：长度 &lt; {@code shortThreshold}（默认 maxChars/10，最少 50 字符）的 chunk
 *       与下个 chunk 合并；避免检索时丢失太短碎片</li>
 *   <li><b>长二分</b>：长度 &gt; {@code longThreshold}（默认 1.5 × maxChars）的 chunk 对半拆；
 *       防止某些策略（如 {@link RecursiveStrategy} 退化成单字符切割）在最后兜底时切出过厚块，
 *       Embedding 模型对超长输入的语义聚焦能力会下降</li>
 * </ol>
 *
 * <p>使用：{@link RecursiveStrategy#split(String, SplitContext)} 调用本类；
 * QA / FIXED_OVERLAP 自身算法已稳定，<b>不强制</b>走 Normalizer（R1 自测再决定是否接入）。
 */
@Slf4j
public final class ChunkNormalizer {

    private ChunkNormalizer() {
    }

    /** {@link Normalizer#normalize(String, java.text.Normalizer.Form)} 的 NFC 包装 */
    public static String nfc(String text) {
        return text == null ? null : Normalizer.normalize(text, Normalizer.Form.NFC);
    }

    /**
     * 短合并 + 长二分；不修改字符偏移区间，只动 {@link RawChunk#content()} 长度与 chunk 数量。
     *
     * @param chunks   输入切片
     * @param maxChars 切长上限（{@link SplitContext#getMaxChars()}）
     * @return 归一化后的切片列表
     */
    public static List<RawChunk> normalize(List<RawChunk> chunks, int maxChars) {
        if (chunks == null || chunks.isEmpty() || maxChars <= 0) {
            return chunks;
        }
        int shortThreshold = Math.max(50, maxChars / 10);
        int longThreshold = (int) (maxChars * 1.5);

        List<RawChunk> result = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            RawChunk cur = chunks.get(i);
            int len = cur.content().length();

            if (len < shortThreshold && i + 1 < chunks.size()) {
                // 短合并到下一个 chunk；区间扩到 next.charEnd
                RawChunk next = chunks.get(i + 1);
                String mergedContent = cur.content() + next.content();
                result.add(RawChunk.of(mergedContent, cur.charStart(), next.charEnd()));
                i++; // 跳过 next（已吞并）
            } else if (len > longThreshold) {
                // 长二分：以字符中点为界拆开
                int half = len / 2;
                int midOffset = cur.charStart() + half;
                String left = cur.content().substring(0, half);
                String right = cur.content().substring(half);
                result.add(RawChunk.of(left, cur.charStart(), midOffset));
                result.add(RawChunk.of(right, midOffset, cur.charEnd()));
                log.debug("[RAG] CHUNK 超长二分: 原长 {} → {} + {}", len, left.length(), right.length());
            } else {
                result.add(cur);
            }
        }
        return result;
    }
}
