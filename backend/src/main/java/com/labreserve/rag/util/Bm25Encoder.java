package com.labreserve.rag.util;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

/**
 * BM25 稀疏向量编码器（把文本编码成 Qdrant sparse vector）。
 *
 * <h3>为什么需要它</h3>
 * dense 向量（text-embedding-v3）在<b>语义</b>上强，但对<b>关键词精确匹配</b>弱：
 * 问"离心机的转速上限"时，若文档写的是"最大转速不得超过 15000 rpm"，
 * dense 仍可能因为字面差异而漏检。BM25 稀疏向量补上这块，
 * 两者用 Qdrant 原生 {@code Fusion.RRF} 融合（见 {@code QdrantAdapter.hybridSearch}）。
 *
 * <h3>中文分词策略</h3>
 * 中文没有空格分隔，本实现采用 <b>unigram + bigram</b> 双粒度（业界常用的轻量方案，
 * 不引入 jieba/analyzer 依赖）：
 * <ul>
 *   <li><b>bigram</b>："离心机" → 离心 / 心机。召回精度高，是主要信号</li>
 *   <li><b>unigram</b>：单字。保证部分匹配也能召回（召回率兜底）</li>
 * </ul>
 * 权重上给 bigram 加权（{@link #BIGRAM_WEIGHT}），因为 bigram 的区分度更高。
 *
 * <h3>ASCII / 数字</h3>
 * 英文与数字按非字母数字切分后小写化（{@code "TikaAdapter"} 与 {@code "tikaadapter"} 同词）。
 *
 * <h3>IDF 由服务端算</h3>
 * 本类只产出 <b>tf</b>（词频）；collection 建索引时声明
 * {@code SparseVectorParams.modifier = Modifier.Idf}，
 * Qdrant 检索时用自身语料统计的 IDF 加权 —— 无需在应用侧维护全局词表/文档频率。
 *
 * @author lab_reserve
 */
public final class Bm25Encoder {

    /** bigram 权重（相对 unigram）。bigram 区分度更高，给更高权重 */
    private static final float BIGRAM_WEIGHT = 2.0f;

    /** unigram 权重 */
    private static final float UNIGRAM_WEIGHT = 1.0f;

    /** 单条文本最多产出的 term 数（防止超长文本撑爆稀疏向量；演示语料 500 字足够） */
    private static final int MAX_TERMS = 4096;

    private Bm25Encoder() {
    }

    /**
     * 编码后的稀疏向量。
     *
     * @param indices 词项 hash 后的 u32 下标（Qdrant sparse 要求）
     * @param values  对应的 tf 权重
     */
    public record SparseVector(int[] indices, float[] values) {

        public boolean isEmpty() {
            return indices == null || indices.length == 0;
        }

        public int size() {
            return indices == null ? 0 : indices.length;
        }
    }

    /**
     * 把文本编码成 BM25 稀疏向量。
     *
     * @param text 原文（调用方应先做 sanitize；空串返回空向量）
     */
    public static SparseVector encode(String text) {
        if (text == null || text.isBlank()) {
            return new SparseVector(new int[0], new float[0]);
        }

        // term → tf 累加（LinkedHashMap 保证确定性，便于测试与复现）
        Map<Integer, Float> termTf = new LinkedHashMap<>();

        // 逐字符扫描：CJK 走 unigram+bigram，ASCII 走整词
        String lower = text.toLowerCase();
        int n = lower.length();
        StringBuilder asciiWord = new StringBuilder();

        for (int i = 0; i < n; i++) {
            char c = lower.charAt(i);

            if (isCjk(c)) {
                // 先把挂着的 ASCII 词冲刷掉
                flushAsciiWord(asciiWord, termTf);

                // unigram
                addTerm(termTf, String.valueOf(c), UNIGRAM_WEIGHT);

                // bigram：当前字 + 下一字（如果都是 CJK）
                if (i + 1 < n && isCjk(lower.charAt(i + 1))) {
                    addTerm(termTf, new StringBuilder().append(c).append(lower.charAt(i + 1)).toString(),
                            BIGRAM_WEIGHT);
                }
            } else if (Character.isLetterOrDigit(c)) {
                asciiWord.append(c);
            } else {
                // 标点/空白：终止当前 ASCII 词
                flushAsciiWord(asciiWord, termTf);
            }

            if (termTf.size() >= MAX_TERMS) {
                break;
            }
        }
        // 文本末尾可能还挂着一个 ASCII 词
        flushAsciiWord(asciiWord, termTf);

        return toSparseVector(termTf);
    }

    // ------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------

    /** 把挂起的 ASCII 词作为一个 term 加入 */
    private static void flushAsciiWord(StringBuilder word, Map<Integer, Float> termTf) {
        if (word.isEmpty()) {
            return;
        }
        addTerm(termTf, word.toString(), UNIGRAM_WEIGHT);
        word.setLength(0);
    }

    private static void addTerm(Map<Integer, Float> termTf, String term) {
        addTerm(termTf, term, UNIGRAM_WEIGHT);
    }

    /** term → CRC32 → u32 下标，tf 累加权重 */
    private static void addTerm(Map<Integer, Float> termTf, String term, float weight) {
        if (term == null || term.isEmpty()) {
            return;
        }
        CRC32 crc = new CRC32();
        crc.update(term.getBytes(StandardCharsets.UTF_8));
        int idx = (int) crc.getValue();
        termTf.merge(idx, weight, Float::sum);
    }

    private static SparseVector toSparseVector(Map<Integer, Float> termTf) {
        if (termTf.isEmpty()) {
            return new SparseVector(new int[0], new float[0]);
        }
        List<Integer> indices = new ArrayList<>(termTf.size());
        List<Float> values = new ArrayList<>(termTf.size());
        for (Map.Entry<Integer, Float> e : termTf.entrySet()) {
            indices.add(e.getKey());
            values.add(e.getValue());
        }
        int[] idxArr = new int[indices.size()];
        float[] valArr = new float[values.size()];
        for (int i = 0; i < idxArr.length; i++) {
            idxArr[i] = indices.get(i);
            valArr[i] = values.get(i);
        }
        return new SparseVector(idxArr, valArr);
    }

    /**
     * 是否 CJK 汉字（含常用扩展区，不含假名/谚文——本项目知识库以中文为主）。
     */
    private static boolean isCjk(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF)      // CJK 统一表意文字
                || (c >= 0x3400 && c <= 0x4DBF)   // 扩展 A
                || (c >= 0xF900 && c <= 0xFAFF);   // 兼容表意文字
    }
}