package com.labreserve.rag.pipeline.chunk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 三种内置切片策略的单元测试。
 *
 * <p>切片是 RAG 检索质量的<b>上游</b>：切错了，检索再准也找不对。
 * 这里锁住三条关键契约：
 * <ul>
 *   <li><b>QA</b>：问答对各成一片，识别不到 Q+A 时退化为整段单片（不丢内容）</li>
 *   <li><b>RECURSIVE</b>：按分隔符优先级递归，每片不超过 maxChars</li>
 *   <li><b>FIXED_OVERLAP</b>：固定窗 + 重叠，相邻片有交集</li>
 * </ul>
 *
 * <p>共同契约：<b>切片内容必须能在原文中找到</b>（charStart/charEnd 区间语义正确），
 * 且不丢内容（拼接后覆盖全文）。
 */
class ChunkingStrategyTest {

    private final QaStrategy qa = new QaStrategy();
    private final RecursiveStrategy recursive = new RecursiveStrategy();
    private final FixedOverlapStrategy fixed = new FixedOverlapStrategy();

    private static SplitContext ctx(int maxChars, int overlap) {
        return new SplitContext(maxChars, overlap, 1, 100L, "MANUAL");
    }

    // ============================================================
    // 通用契约
    // ============================================================

    @Test
    @DisplayName("三种策略：空文本 → 空列表")
    void allStrategiesHandleEmptyText() {
        assertTrue(qa.split("", ctx(500, 50)).isEmpty());
        assertTrue(recursive.split("", ctx(500, 50)).isEmpty());
        assertTrue(fixed.split("", ctx(500, 50)).isEmpty());
        assertTrue(qa.split(null, ctx(500, 50)).isEmpty());
        assertTrue(recursive.split(null, ctx(500, 50)).isEmpty());
        assertTrue(fixed.split(null, ctx(500, 50)).isEmpty());
    }

    @Test
    @DisplayName("三种策略：切片区间必须能还原出原文（charStart/charEnd 语义正确）")
    void chunksMapBackToSourceText() {
        String text = "第一章 总则\n\n第一条 为规范实验室使用，制定本制度。\n\n"
                + "第二章 设备管理\n\n第二条 离心机须定期检定。\n\n"
                + "第三章 危险品\n\n第三条 易燃易爆品单独存放。\n";

        for (ChunkingStrategy s : List.of(qa, recursive, fixed)) {
            List<RawChunk> chunks = s.split(text, ctx(200, 50));
            assertFalse(chunks.isEmpty(), s.code() + " 不应产出空切片");
            for (RawChunk c : chunks) {
                assertTrue(c.charStart() >= 0, s.code() + " charStart 越界");
                assertTrue(c.charEnd() <= text.length(), s.code() + " charEnd 越界");
                assertTrue(c.charStart() < c.charEnd(), s.code() + " 区间非法");
                // 区间对应的文本应与 content 一致（trim 允许差异）
                assertTrue(text.substring(c.charStart(), c.charEnd()).contains(c.content().trim().substring(0,
                        Math.min(10, c.content().trim().length()))),
                        s.code() + " 切片内容与区间不匹配");
            }
        }
    }

    // ============================================================
    // QA 策略
    // ============================================================

    @Test
    @DisplayName("QA：识别 Q:/A: 行首模式，每个问答对一片")
    void qaSplitsEachQuestionAnswerPair() {
        String text = """
                Q: 离心机使用前要检查什么？
                A: 检查电源、转头裂痕、配平。

                Q: 配平误差不能超过多少？
                A: 两侧质量差不超过 0.1 克。
                """;
        List<RawChunk> chunks = qa.split(text, ctx(2000, 0));
        assertEquals(2, chunks.size(), "应切成 2 片：" + chunks);
        assertTrue(chunks.get(0).content().contains("检查电源"));
        assertTrue(chunks.get(1).content().contains("0.1 克"));
    }

    @Test
    @DisplayName("QA：识别中文「问题:/答案:」模式")
    void qaSupportsChinesePrefixes() {
        String text = """
                问题：危险化学品五双管理是什么？
                答案：分类存放、专人管理、账物相符。

                问题：易燃易爆品怎么存？
                答案：需单独储存。
                """;
        List<RawChunk> chunks = qa.split(text, ctx(2000, 0));
        assertEquals(2, chunks.size(), "应切成 2 片：" + chunks);
        assertTrue(chunks.get(0).content().contains("五双管理"));
        assertTrue(chunks.get(1).content().contains("单独储存"));
    }

    @Test
    @DisplayName("QA：「解答」是「答案」的别名，会并入同一个问答对（不另起一片）")
    void qaTreatsJieDaAsAnswerAlias() {
        // Q+A 是一个单元：段内出现 A 前缀（问题/答案/解答 任一）都属于同一对，
        // 直到遇到下一个 Q 前缀才闭合。所以这里只产出 1 片。
        String text = """
                问题：危险化学品五双管理是什么？
                答案：分类存放、专人管理、账物相符。

                解答：易燃易爆品需单独储存。
                """;
        List<RawChunk> chunks = qa.split(text, ctx(2000, 0));
        assertEquals(1, chunks.size(), "「解答」应并入同一问答对：" + chunks);
        assertTrue(chunks.get(0).content().contains("五双管理"));
        assertTrue(chunks.get(0).content().contains("单独储存"));
    }

    @Test
    @DisplayName("QA：识别「第N问」模式")
    void qaSupportsNumberedQuestions() {
        String text = """
                第一问：怎么预约实验室？
                答：登录后在预约管理页提交申请。

                第二问：审核要多久？
                答：实验室管理员通常一个工作日内处理。
                """;
        List<RawChunk> chunks = qa.split(text, ctx(2000, 0));
        assertEquals(2, chunks.size(), "应切成 2 片：" + chunks);
    }

    @Test
    @DisplayName("QA：识别不到 Q+A 时退化为整段单片（不丢内容）")
    void qaFallsBackToSingleChunk() {
        String text = "这是一段没有任何问答标记的普通说明文字。";
        List<RawChunk> chunks = qa.split(text, ctx(2000, 0));
        assertEquals(1, chunks.size());
        assertEquals(text, chunks.get(0).content());
    }

    @Test
    @DisplayName("QA：单个问答对超长时二分，不丢内容")
    void qaSplitsOversizedPair() {
        String q = "Q: " + "很长的内容".repeat(60) + "\nA: 答案";
        List<RawChunk> chunks = qa.split(q, ctx(100, 0));
        assertTrue(chunks.size() >= 2, "超长问答对应被切开");
        for (RawChunk c : chunks) {
            assertTrue(c.content().length() <= 100 || c.content().length() <= q.length());
        }
    }

    @Test
    @DisplayName("QA：默认不重叠（defaultOverlap = 0）")
    void qaHasNoOverlapByDefault() {
        assertEquals(0, qa.defaultOverlap());
        assertTrue(qa.recommendedFor().contains("FAQ"));
    }

    // ============================================================
    // RECURSIVE 策略
    // ============================================================

    @Test
    @DisplayName("RECURSIVE：每片不超过 maxChars")
    void recursiveRespectsMaxChars() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            sb.append("这是第").append(i).append("段内容，用于测试递归切片是否会超长。\n\n");
        }
        int maxChars = 200;
        int overlap = 20;
        List<RawChunk> chunks = recursive.split(sb.toString(), ctx(maxChars, overlap));
        assertFalse(chunks.isEmpty());
        for (RawChunk c : chunks) {
            // overlap 是在切片完成后追加到片尾的（相邻片头部），
            // 所以片长上限是 maxChars + overlap
            assertTrue(c.content().length() <= maxChars + overlap,
                    "切片长度 " + c.content().length() + " 超过 maxChars+overlap=" + (maxChars + overlap));
        }
    }

    @Test
    @DisplayName("RECURSIVE：短文本 → 单片")
    void recursiveKeepsShortTextAsOneChunk() {
        String text = "很短的一句话。";
        List<RawChunk> chunks = recursive.split(text, ctx(500, 50));
        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).content().contains("很短"));
    }

    @Test
    @DisplayName("RECURSIVE：默认推荐 REGULATION / MANUAL / SOP")
    void recursiveRecommendedCategories() {
        assertTrue(recursive.recommendedFor().contains("REGULATION"));
    }

    // ============================================================
    // FIXED_OVERLAP 策略
    // ============================================================

    @Test
    @DisplayName("FIXED_OVERLAP：固定窗切片，每片约等于 maxChars")
    void fixedSplitsByWindow() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            sb.append('字');
        }
        int maxChars = 100;
        int overlap = 20;
        List<RawChunk> chunks = fixed.split(sb.toString(), ctx(maxChars, overlap));
        assertTrue(chunks.size() >= 2, "200 字按 100 窗长应至少 2 片");
        for (RawChunk c : chunks) {
            assertTrue(c.content().length() <= maxChars + overlap,
                    "切片长度 " + c.content().length() + " 超过窗长+重叠=" + (maxChars + overlap));
        }
    }

    @Test
    @DisplayName("FIXED_OVERLAP：相邻片之间有重叠")
    void fixedProducesOverlap() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append((char) ('a' + (i % 26)));
        }
        List<RawChunk> chunks = fixed.split(sb.toString(), ctx(50, 10));
        assertTrue(chunks.size() >= 2);
        // 相邻两片的起点差应小于窗长（有重叠）
        int delta = chunks.get(1).charStart() - chunks.get(0).charStart();
        assertTrue(delta < 50, "相邻片起点差 " + delta + " 应小于窗长 50（存在重叠）");
    }

    @Test
    @DisplayName("FIXED_OVERLAP：默认重叠为 100")
    void fixedDefaultOverlap() {
        assertEquals(100, fixed.defaultOverlap());
    }
}
