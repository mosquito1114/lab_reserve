package com.labreserve.rag.pipeline.chunk;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * QA 问答对切片策略（design.md §11.3 第 10 项）。
 *
 * <p>识别行首模式组成的 Q+A 对，每对切为一个 chunk。
 *
 * <p><b>Q 前缀</b>（任一匹配）：
 * <ul>
 *   <li>{@code "Q:" / "Q." / "Q："} —— 英文冒号 / 句号 / 全角冒号（含可选空白）</li>
 *   <li>{@code "问题:" / "问题："} —— 中文"问题"前缀</li>
 *   <li>{@code "第N问"} —— N 为一到十百 / 阿拉伯数字（如 第3问、第十二问）</li>
 * </ul>
 *
 * <p><b>A 前缀</b>（任一匹配）：
 * <ul>
 *   <li>{@code "A:" / "A." / "A："}</li>
 *   <li>{@code "答案:" / "答案："}</li>
 *   <li>{@code "解答:" / "解答："}</li>
 * </ul>
 *
 * <p>扫描规则：逐行扫；遇到 Q_PREFIX 起点开；遇到下一个 Q_PREFIX（即下个问题）或 EOF 闭。
 * 每个 Q+A 对里的内容字符数若仍超过 {@code maxChars}（默认 2000），由
 * {@link ChunkNormalizer} 长二分兜底。
 *
 * <p>QA <b>不消费 overlap</b>——{@link #defaultOverlap()} 返回 0，问答对是天然独立语义单元。
 *
 * <p>推荐场景：{@code FAQ}（FAQ 类文档天然一问一答）；不推荐 REGULATION / SOP 这类连续文体。
 */
@Slf4j
@Component
public class QaStrategy implements ChunkingStrategy {

    /**
     * Q 前缀正则：行首是 {@code Q[.:：]} / {@code 问题[:：]?} / {@code 第[一...百\d]+问}。
     * 实际匹配时我们另外 trim 行首空白再调 {@link Matcher#find()}，所以 Pattern 不带 {@code ^}。
     */
    private static final Pattern Q_PREFIX = Pattern.compile(
            "^(Q[\\.:：]\\s*" +
                    "|问题[:：]?\\s*" +
                    "|第[一二三四五六七八九十百\\d]+问\\s*)"
    );

    /** A 前缀正则。 */
    private static final Pattern A_PREFIX = Pattern.compile(
            "^(A[\\.:：]\\s*" +
                    "|答案[:：]?\\s*" +
                    "|解答[:：]?\\s*)"
    );

    @Override
    public String code() { return "QA"; }

    @Override
    public String name() { return "问答对分块"; }

    @Override
    public String description() {
        return "识别 Q / A / 问题 / 答案 / 解答 等行首模式，每对 Q+A 切为一片。"
                + "适合 FAQ 类文档；默认不重叠。";
    }

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
        if (cleanText == null || cleanText.isEmpty()) {
            return List.of();
        }
        String text = ChunkNormalizer.nfc(cleanText).trim();
        if (text.isEmpty()) {
            return List.of();
        }
        int maxChars = Math.max(1, ctx.getMaxChars());

        List<int[]> qaPairs = findQAPairs(text);
        if (qaPairs.isEmpty()) {
            // 没识别出 Q+A 模式，退路：整段当一个 chunk
            log.debug("[RAG][QA] 未识别到 Q+A 模式, 整段单 chunk");
            return List.of(RawChunk.of(text, 0, text.length()));
        }

        List<RawChunk> chunks = new ArrayList<>(qaPairs.size());
        for (int[] span : qaPairs) {
            String content = text.substring(span[0], span[1]);
            if (content.length() > maxChars) {
                // 单 Q+A 对自身就过长；二分（保留 RawChunk 区间语义）
                int mid = span[0] + content.length() / 2;
                chunks.add(RawChunk.of(text.substring(span[0], mid), span[0], mid));
                chunks.add(RawChunk.of(text.substring(mid, span[1]), mid, span[1]));
            } else {
                chunks.add(RawChunk.of(content, span[0], span[1]));
            }
        }

        log.debug("[RAG][QA] 切片完成: 总长={} maxChars={} qaPairs={} chunks={}",
                text.length(), maxChars, qaPairs.size(), chunks.size());
        return chunks;
    }

    /**
     * 在 {@code text} 找出所有 Q+A 对的字符区间。
     *
     * <p>线性扫描：从前往后逐行检查；遇到 Q_PREFIX 起点开一段；
     * 段内仅允许出现 A_PREFIX（直到下一个 Q_PREFIX 或 EOF 才闭合）。
     */
    private List<int[]> findQAPairs(String text) {
        List<int[]> pairs = new ArrayList<>();
        int len = text.length();
        int pos = 0;

        while (pos < len) {
            int qStart = findLineOfType(text, pos, Q_PREFIX);
            if (qStart < 0) {
                break;
            }
            // 找下一个 Q 作为闭合
            int nextQ = findLineOfType(text, qStart + 1, Q_PREFIX);
            int end = nextQ >= 0 ? nextQ : len;

            // [qStart, end) 区间；去掉首尾空行（用 trim）
            int trimStart = qStart;
            int trimEnd = end;
            // 左 trim：跳前导空白
            while (trimStart < trimEnd && Character.isWhitespace(text.charAt(trimStart))) {
                trimStart++;
            }
            // 右 trim：跳末尾空白（不含 \n 之前的所有空白）
            while (trimEnd > trimStart && Character.isWhitespace(text.charAt(trimEnd - 1))) {
                trimEnd--;
            }
            if (trimEnd > trimStart) {
                pairs.add(new int[]{trimStart, trimEnd});
            }
            pos = end;
        }
        return pairs;
    }

    /**
     * 从 {@code from} 位置开始扫逐行，找到第一个匹配 {@code pattern} 的<b>行起始位置</b>。
     * 找不到返 -1。
     */
    private int findLineOfType(String text, int from, Pattern pattern) {
        int len = text.length();
        int pos = from;
        while (pos < len) {
            int lineEnd = text.indexOf('\n', pos);
            if (lineEnd < 0) lineEnd = len;
            String line = text.substring(pos, lineEnd);
            // 行内允许前置空白
            String trimmed = line.replaceFirst("^\\s+", "");
            if (pattern.matcher(trimmed).find()) {
                return pos;
            }
            pos = lineEnd + 1;
        }
        return -1;
    }
}
