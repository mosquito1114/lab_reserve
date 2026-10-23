package com.labreserve.rag.service;

import com.labreserve.rag.config.RagProperties;
import com.labreserve.rag.model.vo.CitationVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Step 4.5 · RelevanceJudge（检索结果相关性仲裁）。
 *
 * <h3>为什么需要这一步（R4 联调实证）</h3>
 * 实测 text-embedding-v3 的 cosine 分布：
 * <pre>
 *   正样本「离心机使用前要检查什么」      0.7121
 *   正样本「危险化学品五双管理是什么」    0.4994
 *   正样本「易燃易爆化学品怎么存放」      0.4508
 *   负样本「量子计算机的低温制冷原理」    0.4638  ← 夹在正样本中间！
 * </pre>
 * 负样本分数<b>落在正样本区间内部</b>，任何单一 cosine 阈值都无法分开。
 * 这是小语料（2 个 chunk）下的固有局限：不相关的中文句子在嵌入空间里也会靠得很近。
 *
 * <h3>三段式门禁（Corrective RAG 标准做法）</h3>
 * <pre>
 *   dense >= acceptScore (0.60)          → 直接放行（明确相关，省一次 LLM）
 *   dense <  rejectScore (0.35)          → 直接拒（明确不相关，省一次 LLM）
 *   rejectScore <= dense < acceptScore   → 【灰区】调 cheapLlm 判「证据能否回答问题」
 *                                           YES → 放行；NO → 硬拒
 * </pre>
 * 这样既保证「零幻觉」（灰区由 LLM 兜住），又把额外 LLM 调用限制在少数灰区请求上
 * （实测 6 条样本里只有 3 条进灰区）。
 *
 * <h3>与 v3 设计的关系</h3>
 * rag-design §12.6 说「未命中 → 硬拒，不调 LLM」——这里<b>不是</b>调 LLM 去"作答"，
 * 而是调 LLM 判断"证据是否足够"（一个二分类 Yes/No，不产生任何正文），
 * 判定为否时仍走硬拒、仍不生成答案。语义上与零幻觉原则一致。
 */
@Slf4j
@Service
public class RelevanceJudge {

    private static final String PROMPT = """
            你是检索结果的相关性判定器。

            给用户的问题和检索到的证据，判断：**这些证据是否足以回答用户的问题？**

            判定标准：
            - 证据必须直接涉及问题所问的对象与主题，才算足以回答；
            - 仅仅"同属实验室/安全/化学"这类宽泛关联，**不算**足以回答；
            - 证据讲的是别的东西（例如问量子计算、证据讲离心机），一定判 NO。

            只输出一个词：YES 或 NO。不要解释。

            【问题】%s

            【证据】
            %s
            """;

    @Autowired
    private ChatClient cheapLlm;

    @Autowired
    private RagProperties props;

    /** 判定结果 */
    public record Verdict(boolean relevant, String reason) {
    }

    /**
     * 三段式判定。
     *
     * @param question      用户问题（重写后的检索问句）
     * @param denseTop1     dense 检索的 top1 cosine 分
     * @param evidenceTexts 证据正文（<b>完整 chunk 内容</b>，不是 UI 用的 200 字短 snippet）
     * @param citations     检索命中的引用（用于标题/类别展示）
     * @return 是否放行
     */
    public Verdict judge(String question, double denseTop1,
                         List<String> evidenceTexts, List<CitationVO> citations) {
        if (citations == null || citations.isEmpty()) {
            return new Verdict(false, "无检索结果");
        }

        double accept = props.getChat().getKbAcceptScore();
        double reject = props.getChat().getKbRejectScore();

        // ① 明确相关：直接放行，省一次 LLM 调用
        if (denseTop1 >= accept) {
            return new Verdict(true, "dense %.4f >= %.2f（明确相关）".formatted(denseTop1, accept));
        }
        // ② 明确不相关：直接拒
        if (denseTop1 < reject) {
            return new Verdict(false, "dense %.4f < %.2f（明确不相关）".formatted(denseTop1, reject));
        }

        // ③ 灰区：让 LLM 判「证据能否回答」
        try {
            // ★ 必须传完整 chunk 正文：答案可能在 chunk 中段/尾段，
            //   只传开头 200 字会让判官误判「证据不相关」（R4 联调踩过）
            StringBuilder evidence = new StringBuilder();
            int n = Math.min(3, evidenceTexts.size());
            for (int i = 0; i < n; i++) {
                CitationVO c = i < citations.size() ? citations.get(i) : null;
                String body = truncate(evidenceTexts.get(i), 1200);
                evidence.append("[").append(i + 1).append("] 《")
                        .append(c == null || c.getTitle() == null ? "未命名" : c.getTitle())
                        .append("》\n").append(body).append("\n\n");
            }

            String out = cheapLlm.prompt()
                    .user(PROMPT.formatted(question, evidence.toString()))
                    .call()
                    .content();

            boolean yes = out != null && out.trim().toUpperCase().startsWith("YES");
            log.info("[RAG][JUDGE] 灰区判定: dense={} → {} query={}",
                    String.format("%.4f", denseTop1), yes ? "YES(放行)" : "NO(拒答)",
                    abbreviate(question));
            return new Verdict(yes, "灰区 LLM 判定: " + (yes ? "YES" : "NO"));
        } catch (Exception e) {
            // LLM 不可用 → 保守拒答（宁可漏答，不可幻觉）
            log.warn("[RAG][JUDGE] 灰区判定异常,保守拒答: {}", e.getMessage());
            return new Verdict(false, "相关性判定失败，保守拒答");
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static String abbreviate(String s) {
        if (s == null) return "";
        return s.length() <= 30 ? s : s.substring(0, 30) + "…";
    }
}