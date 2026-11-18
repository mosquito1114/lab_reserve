package com.labreserve.rag.service;

import com.labreserve.rag.config.RagProperties;
import com.labreserve.rag.mapper.RagMessageMapper;
import com.labreserve.rag.mapper.RagSessionMapper;
import com.labreserve.rag.model.entity.RagMessage;
import com.labreserve.rag.model.entity.RagSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 会话摘要压缩（R6 · 激活 {@code tb_rag_session.summary} 这个此前一直没人写的字段）。
 *
 * <h3>为什么需要</h3>
 * {@code chat.history-window=6} 意味着 prompt 里最多只带最近 6 轮对话。
 * 会话聊到第 20 轮时，前 14 轮如果不压缩就<b>彻底消失</b> ——
 * 用户问"我们一开始聊的那个离心机转速的问题"时，LLM 什么都不知道。
 *
 * <p>压缩策略：把"落在最近 N 轮之外"的早期对话用一次 LLM 调用压成一段摘要，
 * 存进 {@code session.summary}，并用 {@code session.summary_upto} 标记覆盖到哪条消息。
 * 下一轮 {@code ContextLoaderService} 会：
 * <ul>
 *   <li>读出 {@code summary} 作为【历史摘要】放进 prompt</li>
 *   <li>跳过 {@code id <= summary_upto} 的消息（避免摘要与原文重复占 token）</li>
 * </ul>
 *
 * <h3>触发条件</h3>
 * 早期消息（摘要覆盖范围之外、且不属于最近 N 轮）达到 {@code chat.summary-trigger-messages} 条（默认 4）。
 * 低于这个数不压缩 —— 压缩本身要花一次 LLM 调用，不值得。
 *
 * <h3>降级</h3>
 * LLM 失败 / 超时 → 不写摘要，原文继续保留在库里（下次再试），<b>不影响主链路</b>。
 */
@Slf4j
@Service
public class SessionSummarizer {

    /** 压缩时最多喂给 LLM 的早期消息条数（防 prompt 爆炸） */
    private static final int MAX_COMPRESS_MESSAGES = 40;

    /** 旧摘要 + 新早期对话的总量上限（字符） */
    private static final int MAX_SUMMARY_CHARS = 1200;

    @Autowired
    private RagMessageMapper messageMapper;

    @Autowired
    private RagSessionMapper sessionMapper;

    @Autowired
    private ChatClient cheapLlm;

    @Autowired
    private RagProperties props;

    /**
     * 尝试压缩会话早期对话。**永不抛异常** —— 失败只记日志。
     *
     * @param session 当前会话（需带 id / summary / summaryUpTo）
     * @return 是否发生了压缩
     */
    public boolean maybeSummarize(RagSession session) {
        if (session == null || session.getId() == null) {
            return false;
        }
        try {
            int window = Math.max(1, props.getChat().getHistoryWindow());
            int keepRecent = window * 2;
            int trigger = Math.max(2, props.getChat().getSummaryTriggerMessages());

            // 取全量消息（上限 MAX_COMPRESS_MESSAGES * 2，够用且不爆）
            List<RagMessage> fetched = messageMapper.listRecent(session.getId(), MAX_COMPRESS_MESSAGES * 2);
            if (fetched == null || fetched.isEmpty()) {
                return false;
            }
            Collections.reverse(fetched);   // 时间正序

            // 已被摘要覆盖的不再重复压缩
            Long summaryUpTo = session.getSummaryUpTo();
            List<RagMessage> pending = new ArrayList<>();
            for (RagMessage m : fetched) {
                if (summaryUpTo != null && m.getId() != null && m.getId() <= summaryUpTo) {
                    continue;
                }
                pending.add(m);
            }

            // 最近 keepRecent 条留在 prompt 里，不压缩
            if (pending.size() <= keepRecent) {
                return false;
            }
            List<RagMessage> early = pending.subList(0, pending.size() - keepRecent);
            if (early.size() < trigger) {
                return false;
            }
            if (early.size() > MAX_COMPRESS_MESSAGES) {
                early = early.subList(early.size() - MAX_COMPRESS_MESSAGES, early.size());
            }

            String oldSummary = session.getSummary();
            String newSummary = compress(oldSummary, early);
            if (newSummary == null || newSummary.isBlank()) {
                return false;
            }
            if (newSummary.length() > MAX_SUMMARY_CHARS) {
                newSummary = newSummary.substring(0, MAX_SUMMARY_CHARS) + "…";
            }

            long upto = early.get(early.size() - 1).getId();
            sessionMapper.updateSummary(session.getId(), newSummary, upto);
            session.setSummary(newSummary);
            session.setSummaryUpTo(upto);

            log.info("[RAG][SUMMARY] sessionId={} 压缩早期 {} 条 → 摘要 {} 字, summaryUpTo={}",
                    session.getId(), early.size(), newSummary.length(), upto);
            return true;
        } catch (Exception e) {
            log.warn("[RAG][SUMMARY] 压缩失败（不影响主链路）: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 用 LLM 把早期对话压成摘要。
     *
     * @param oldSummary 已有摘要（可空）
     * @param early     待压缩的早期消息
     * @return 摘要正文；失败返回 null
     */
    private String compress(String oldSummary, List<RagMessage> early) {
        StringBuilder convo = new StringBuilder();
        for (RagMessage m : early) {
            String role = "USER".equals(m.getRole()) ? "用户" : "助手";
            String content = m.getContent() == null ? "" : m.getContent().replaceAll("\\s+", " ").trim();
            if (content.length() > 300) {
                content = content.substring(0, 300) + "…";
            }
            convo.append(role).append("：").append(content).append('\n');
        }

        String prompt = """
                请把下面这段实验室知识库问答对话压缩成一段简短的摘要，供后续对话回顾。

                要求：
                1) 只保留**讨论过的主题与已给出的关键结论**（尤其是具体数值、参数、规则）；
                2) 不要写"用户问了什么"这种过程描述，直接写结论要点，每行一条，以"· "开头；
                3) 不要添加对话里没有的信息；
                4) 控制在 8 条以内、600 字以内；
                5) 严格输出摘要正文，不要任何解释、不要标题。

                【此前摘要】
                %s
                【需要并入摘要的对话】
                %s
                """.formatted(
                (oldSummary == null || oldSummary.isBlank()) ? "（无）" : oldSummary.trim(),
                convo);

        try {
            String out = cheapLlm.prompt()
                    .user(prompt)
                    .call()
                    .content();
            if (out == null || out.isBlank()) {
                return null;
            }
            String cleaned = out.trim()
                    .replaceAll("^```[a-zA-Z]*\\s*", "")
                    .replaceAll("```$", "")
                    .trim();
            return cleaned.isEmpty() ? null : cleaned;
        } catch (Exception e) {
            log.warn("[RAG][SUMMARY] LLM 压缩失败: {}", e.getMessage());
            return null;
        }
    }
}
