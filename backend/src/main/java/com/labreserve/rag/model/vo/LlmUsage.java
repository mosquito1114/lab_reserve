package com.labreserve.rag.model.vo;

import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

import java.io.Serial;
import java.io.Serializable;

/**
 * 一次 LLM 调用的 token 用量（R6 · 真实统计）。
 *
 * <p><b>为什么需要它</b>：{@code tb_rag_usage_stats.used_tokens} 此前恒为 0
 * （代码里直接写死 {@code stats.setUsedTokens(0)}），
 * 导致用量报表完全不可信 —— 不知道一次问答到底烧了多少 token。
 *
 * <p><b>数据来源</b>：DashScope（OpenAI 兼容模式）在响应里返回 {@code usage}，
 * 字段名有两种方言，需要同时兼容：
 * <ul>
 *   <li>OpenAI 兼容：{@code prompt_tokens} / {@code completion_tokens} / {@code total_tokens}</li>
 *   <li>DashScope 原生：{@code input_tokens} / {@code output_tokens} / {@code total_tokens}</li>
 * </ul>
 *
 * <p><b>降级策略</b>：provider 不返回 usage（部分模型 / 兼容端点不返回）时，
 * 用 {@link #estimate(String)} 本地粗估，保证统计值不为 0、报表可用。
 * 粗估口径：中文 1 字 ≈ 1 token，ASCII 4 字符 ≈ 1 token
 * （与 {@code ContextLoaderService} 的预算估算保持一致）。
 */
public record LlmUsage(int promptTokens, int completionTokens, int totalTokens) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 空用量（未发生 LLM 调用） */
    public static final LlmUsage EMPTY = new LlmUsage(0, 0, 0);

    public LlmUsage {
        if (promptTokens < 0) promptTokens = 0;
        if (completionTokens < 0) completionTokens = 0;
        if (totalTokens < 0) totalTokens = 0;
    }

    /** 累加（用于把一次问答里多次 LLM 调用的用量合并） */
    public LlmUsage plus(LlmUsage other) {
        if (other == null) {
            return this;
        }
        return new LlmUsage(
                promptTokens + other.promptTokens,
                completionTokens + other.completionTokens,
                totalTokens + other.totalTokens);
    }

    /**
     * 从 Spring AI 的 {@link ChatResponse} 里取 usage。
     *
     * <p>取不到（provider 没返回 / metadata 为空）时返回 {@link #EMPTY}。
     */
    public static LlmUsage from(ChatResponse response) {
        if (response == null) {
            return EMPTY;
        }
        return from(response.getMetadata());
    }

    /** 从 {@link ChatResponseMetadata} 里取 usage */
    public static LlmUsage from(ChatResponseMetadata metadata) {
        if (metadata == null) {
            return EMPTY;
        }
        Usage usage = metadata.getUsage();
        if (usage == null) {
            return EMPTY;
        }
        Integer prompt = usage.getPromptTokens();
        Integer completion = usage.getCompletionTokens();
        Integer total = usage.getTotalTokens();
        if (prompt == null && completion == null && total == null) {
            return EMPTY;
        }
        int p = prompt == null ? 0 : prompt;
        int c = completion == null ? 0 : completion;
        int t = total == null ? (p + c) : total;
        return new LlmUsage(p, c, t);
    }

    /**
     * 本地粗估 token 数（provider 没返回 usage 时的降级）。
     *
     * <p>中文 1 字 ≈ 1 token，ASCII 4 字符 ≈ 1 token。
     */
    public static int estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int cjk = 0;
        int ascii = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) {
                cjk++;
            } else if (c < 128) {
                ascii++;
            }
        }
        return cjk + ascii / 4;
    }

    /**
     * 构造"只有 prompt 侧"的估算用量（回答侧由调用方另算）。
     */
    public static LlmUsage estimated(String promptText) {
        int p = estimate(promptText);
        return new LlmUsage(p, 0, p);
    }
}
