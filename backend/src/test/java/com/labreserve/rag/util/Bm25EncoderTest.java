package com.labreserve.rag.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Bm25Encoder} 单元测试。
 *
 * <p>BM25 稀疏向量是 Hybrid 检索的"稀疏腿"，与 dense 腿一起送 Qdrant 做 RRF 融合。
 * 编码错了不会报错，只会**静默召回变差** —— 所以必须钉住。
 *
 * <p>实现要点：中文走 unigram(权重 1) + bigram(权重 2)，ASCII 走整词，
 * term 经 CRC32 映射成 u32 下标，同一 term 的 tf 权重累加。
 */
class Bm25EncoderTest {

    // ============================================================
    // 基础
    // ============================================================

    @Test
    @DisplayName("空串 / null / 纯空白 → 空向量")
    void shouldReturnEmptyVectorForBlank() {
        assertTrue(Bm25Encoder.encode(null).isEmpty());
        assertTrue(Bm25Encoder.encode("").isEmpty());
        assertTrue(Bm25Encoder.encode("   \n\t ").isEmpty());
        assertEquals(0, Bm25Encoder.encode(null).size());
    }

    @Test
    @DisplayName("indices 与 values 长度必须一致，且按 term 出现顺序稳定排列")
    void shouldKeepIndicesAndValuesAligned() {
        Bm25Encoder.SparseVector v = Bm25Encoder.encode("危险化学品五双管理");
        assertEquals(v.indices().length, v.values().length);
        assertTrue(v.size() > 0);
        // 确定性：同输入两次编码结果完全一致（LinkedHashMap 保证）
        Bm25Encoder.SparseVector again = Bm25Encoder.encode("危险化学品五双管理");
        assertArrayEquals(v.indices(), again.indices());
        assertArrayEquals(v.values(), again.values());
    }

    // ============================================================
    // 中文分词
    // ============================================================

    @Test
    @DisplayName("中文同时产生 unigram 与 bigram，bigram 权重更高")
    void shouldEmitUnigramAndBigramForChinese() {
        // "离心机" = 离 心 机 → unigram 离/心/机 + bigram 离心/心机
        Bm25Encoder.SparseVector v = Bm25Encoder.encode("离心机");
        // 3 个 unigram + 2 个 bigram = 5 个不同 term
        assertEquals(5, v.size());

        // bigram 权重 2.0 > unigram 权重 1.0 → 最大权重值应为 2.0
        float max = 0f;
        for (float val : v.values()) {
            max = Math.max(max, val);
        }
        assertEquals(2.0f, max, 0.001f);
    }

    @Test
    @DisplayName("同一 term 重复出现 → tf 权重累加")
    void shouldAccumulateTermFrequency() {
        // 用纯 ASCII 词避免 bigram 干扰："abc" 出现 3 次 → 权重 1+1+1 = 3
        Bm25Encoder.SparseVector ascii = Bm25Encoder.encode("abc abc abc");
        assertEquals(3.0f, maxValue(ascii), 0.001f);

        // 中文同理："样本样本" → unigram "样"×2 + "本"×2，bigram "样本"×2(权重2×2=4)
        //   → 最大权重 = 4（bigram 出现两次）
        Bm25Encoder.SparseVector cjk = Bm25Encoder.encode("样本样本");
        assertEquals(4.0f, maxValue(cjk), 0.001f);
    }

    @Test
    @DisplayName("不同文本 → 不同向量（不是所有 term 都撞同一个下标）")
    void shouldDistinguishDifferentTexts() {
        Set<Integer> a = indicesOf(Bm25Encoder.encode("离心机使用前检查"));
        Set<Integer> b = indicesOf(Bm25Encoder.encode("危险化学品存放"));
        Set<Integer> common = new HashSet<>(a);
        common.retainAll(b);
        // 两段毫无关系的文本，公共 term 应很少（只有偶然的 CRC 碰撞）
        assertTrue(common.size() < Math.min(a.size(), b.size()),
                "不相关文本不应有大量公共 term，实际公共 " + common.size());
    }

    @Test
    @DisplayName("包含共同词的文本 → 共享对应 term 的下标")
    void shouldShareTermIndexForCommonWords() {
        Set<Integer> a = indicesOf(Bm25Encoder.encode("危险化学品存放要求"));
        Set<Integer> b = indicesOf(Bm25Encoder.encode("危险化学品领用流程"));
        Set<Integer> common = new HashSet<>(a);
        common.retainAll(b);
        assertFalse(common.isEmpty(), "共享「危险化学品」应有公共 term");
    }

    // ============================================================
    // 英文 / 混合
    // ============================================================

    @Test
    @DisplayName("英文按整词处理，大小写不敏感")
    void shouldHandleAsciiWordsCaseInsensitively() {
        Bm25Encoder.SparseVector a = Bm25Encoder.encode("Centrifuge SOP");
        Bm25Encoder.SparseVector b = Bm25Encoder.encode("centrifuge sop");
        assertArrayEquals(a.indices(), b.indices());
        assertArrayEquals(a.values(), b.values());
    }

    @Test
    @DisplayName("英文标点是词边界：'a b' 与 'ab' 是不同 term")
    void shouldSplitAsciiOnPunctuation() {
        Bm25Encoder.SparseVector spaced = Bm25Encoder.encode("a b");
        Bm25Encoder.SparseVector joined = Bm25Encoder.encode("ab");
        assertFalse(java.util.Arrays.equals(spaced.indices(), joined.indices()),
                "空格应把 'a b' 切成两个 term");
    }

    @Test
    @DisplayName("中英混排：中文部分与英文部分都进向量")
    void shouldHandleMixedContent() {
        Bm25Encoder.SparseVector v = Bm25Encoder.encode("使用 SOP 离心机");
        // 中文 unigram+bigram + 英文 "sop" 整词
        assertTrue(v.size() > 3);
    }

    // ============================================================
    // 上限保护
    // ============================================================

    @Test
    @DisplayName("超长文本的 term 数被 MAX_TERMS 截断（防 prompt/请求体爆炸）")
    void shouldCapTermCount() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            sb.append("实验");
        }
        Bm25Encoder.SparseVector v = Bm25Encoder.encode(sb.toString());
        assertTrue(v.size() <= 4096, "term 数应被截断在 4096 以内，实际 " + v.size());
    }

    // ============================================================
    // 辅助
    // ============================================================

    private static float maxValue(Bm25Encoder.SparseVector v) {
        float max = 0f;
        for (float val : v.values()) {
            max = Math.max(max, val);
        }
        return max;
    }

    private static Set<Integer> indicesOf(Bm25Encoder.SparseVector v) {
        Set<Integer> s = new HashSet<>();
        for (int i : v.indices()) {
            s.add(i);
        }
        return s;
    }
}
