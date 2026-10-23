package com.labreserve.rag.service;

import com.labreserve.rag.config.RagProperties;
import com.labreserve.rag.mapper.RagMessageMapper;
import com.labreserve.rag.model.entity.RagMessage;
import com.labreserve.rag.model.entity.RagSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Step 1 · ContextLoader（会话上下文装载）。
 *
 * <p>取当前会话的：
 * <ul>
 *   <li>历史摘要 {@code session.summary}（R3 只读，R5 写入压缩结果）</li>
 *   <li>最近 {@code chat.history-window} 轮对话（{@code history-window=6} → 12 条消息）</li>
 * </ul>
 *
 * <p><b>Token 预算</b>：粗估用"中文字符数 ≈ 1 token"（中文单字通常 1 token，英文单词约 4 字符 1 token），
 * 超过 {@code chat.max-context-tokens} 时<b>从最老的一轮开始丢</b>，保证最近的对话总是完整进 prompt。
 *
 * <p>{@link #lastTurnDigest()} 只取最近一轮，作为问题重写的指代消解线索
 * （"它" / "上面说的那个" → 具体是什么）。
 */
@Slf4j
@Service
public class ContextLoaderService {

    @Autowired
    private RagMessageMapper messageMapper;

    @Autowired
    private RagProperties props;

    /** 装载结果：会话 + 历史摘要 + 最近对话（时间正序） */
    public record LoadedContext(
            RagSession session,
            String summary,
            List<RagMessage> recentTurns
    ) {
        /** 最近一轮对话的摘要，供 QueryRewriter 做指代消解 */
        public String lastTurnDigest() {
            if (recentTurns == null || recentTurns.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            // 取最后两条（通常是 USER 问 + ASSISTANT 答）
            int from = Math.max(0, recentTurns.size() - 2);
            for (int i = from; i < recentTurns.size(); i++) {
                RagMessage m = recentTurns.get(i);
                sb.append(m.getRole()).append(": ").append(abbreviate(m.getContent())).append('\n');
            }
            return sb.toString();
        }
    }

    /**
     * 装载会话上下文。
     *
     * @param session      当前会话（已 resolve）
     * @param currentInput 本次用户输入（不计入历史，仅用于日志）
     */
    public LoadedContext load(RagSession session, String currentInput) {
        int historyWindow = Math.max(1, props.getChat().getHistoryWindow());
        int messageLimit = historyWindow * 2;

        // mapper 返回 DESC（最新在前），这里 reverse 成时间正序喂 LLM
        List<RagMessage> recent = messageMapper.listRecent(session.getId(), messageLimit);
        if (recent == null || recent.isEmpty()) {
            recent = Collections.emptyList();
        } else {
            Collections.reverse(recent);
        }

        // Token 预算裁剪（从最老开始丢）
        int budget = props.getChat().getMaxContextTokens();
        List<RagMessage> trimmed = trimToBudget(recent, budget);

        log.debug("[RAG][CTX] sessionId={} 历史={} 条 → 预算内 {} 条 (预算={})",
                session.getId(), recent.size(), trimmed.size(), budget);

        return new LoadedContext(session, session.getSummary(), trimmed);
    }

    /**
     * 从最老的对话开始丢弃，直到总量在 budget 内。
     */
    private List<RagMessage> trimToBudget(List<RagMessage> messages, int budget) {
        if (messages.isEmpty() || budget <= 0) {
            return messages;
        }
        int total = 0;
        for (RagMessage m : messages) {
            total += estimateTokens(m.getContent());
        }
        if (total <= budget) {
            return messages;
        }
        // 从最老开始丢（注意保持 USER/ASSISTANT 成对，不要只留半句）
        List<RagMessage> kept = new java.util.ArrayList<>(messages);
        while (total > budget && kept.size() > 2) {
            RagMessage dropped = kept.remove(0);
            total -= estimateTokens(dropped.getContent());
            // 成对丢弃：如果下一条是 ASSISTANT 而当前留的是孤立的 USER，也一并丢
            if (!kept.isEmpty() && "USER".equals(dropped.getRole())
                    && "ASSISTANT".equals(kept.get(0).getRole())) {
                RagMessage alsoDrop = kept.remove(0);
                total -= estimateTokens(alsoDrop.getContent());
            }
        }
        return kept;
    }

    /** 粗估 token 数：中文 1 字 ≈ 1 token，ASCII 4 字符 ≈ 1 token */
    private static int estimateTokens(String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        int cjk = 0;
        int ascii = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) {
                cjk++;
            } else if (c < 128) {
                ascii++;
            }
        }
        return cjk + ascii / 4;
    }

    private static String abbreviate(String s) {
        if (s == null) return "";
        return s.length() <= 100 ? s : s.substring(0, 100) + "…";
    }
}