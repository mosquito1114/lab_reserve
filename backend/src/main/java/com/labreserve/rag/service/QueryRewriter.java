package com.labreserve.rag.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Step 2 · QueryRewriter（问题重写 / 指代消解）。
 *
 * <p>把"依赖上下文的口语问句"改写成"可独立检索的问句"：
 * <pre>
 * 上轮：离心机的转速能设到多少？
 * 本轮：那它呢？              ← 检索不到"它"
 * 重写：离心机的转速能设到多少？  ← 指代补全后可独立检索
 * </pre>
 *
 * <p><b>不扩张语义</b>：只补全指代与省略，不添加用户没问的内容。
 *
 * <h3>降级策略</h3>
 * 没有历史对话 → 直接透传（省一次 LLM 调用）。
 * LLM 失败/超时 → 透传原问句（宁可检索差一点，也不要整个链路挂掉）。
 */
@Slf4j
@Service
public class QueryRewriter {

    /** 重写结果 */
    public record Rewritten(String question, boolean rewritten) {
        public static Rewritten passthrough(String q) {
            return new Rewritten(q, false);
        }
    }

    @Autowired
    private ChatClient cheapLlm;

    /**
     * 重写问题。
     *
     * @param rawInput      用户原始输入
     * @param lastTurnDigest 最近一轮对话摘要（空 = 无历史）
     */
    public Rewritten rewrite(String rawInput, String lastTurnDigest) {
        if (lastTurnDigest == null || lastTurnDigest.isBlank()) {
            // 没有上下文，直接透传
            return Rewritten.passthrough(rawInput);
        }

        try {
            String prompt = """
                    请把用户的口语化问句改写成「可独立检索」的完整问句。
                    要求：
                    1) 只补全指代与省略（例如"它""那个""上面说的"→ 具体对象），不改变本意、不添加新信息；
                    2) 保留原语言（中文问句输出中文）；
                    3) 只输出改写后的问句本身，不要解释、不要引号。

                    【最近对话】
                    %s
                    【当前问句】
                    %s
                    """.formatted(lastTurnDigest, rawInput);

            String out = cheapLlm.prompt()
                    .user(prompt)
                    .call()
                    .content();

            if (out == null || out.isBlank()) {
                return Rewritten.passthrough(rawInput);
            }
            String cleaned = out.trim().replaceAll("^[\"'“”]+|[\"'“”]+$", "");
            if (cleaned.isEmpty()) {
                return Rewritten.passthrough(rawInput);
            }
            log.debug("[RAG][REWRITE] '{}' → '{}'", rawInput, cleaned);
            return new Rewritten(cleaned, !cleaned.equals(rawInput));

        } catch (Exception e) {
            // LLM 不可用 → 透传
            log.warn("[RAG][REWRITE] 重写失败,透传原问句: {}", e.getMessage());
            return Rewritten.passthrough(rawInput);
        }
    }
}