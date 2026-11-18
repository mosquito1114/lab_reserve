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
 *   <li>历史摘要 {@code session.summary}（由 {@code SessionSummarizer} 写入，压缩早期对话）</li>
 *   <li>最近 {@code chat.history-window} 轮对话（{@code history-window=6} → 12 条消息）</li>
 * </ul>
 *
 * <p><b>Token 预算</b>：粗估用"中文字符数 ≈ 1 token"（中文单字通常 1 token，英文单词约 4 字符 1 token），
 * 超过 {@code chat.max-context-tokens} 时<b>从最老的一轮开始丢</b>，保证最近的对话总是完整进 prompt。
 * 摘要与最近对话<b>共享同一份预算</b>（摘要优先保留 —— 它是压缩过的，丢了就丢信息）。
 *
 * <p>{@link #lastTurnDigest()} 只取最近一轮，作为问题重写的指代消解线索
 * （"它" / "上面说的那个" → 具体是什么）。
 *
 * <p>{@link #historyText()} 渲染整段历史（摘要 + 最近对话）供 AnswerSynthesizer / IntentRouter 使用。
 * <b>这是"上下文记忆"真正生效的地方</b>：R5 之前历史只用来做问题重写，
 * LLM 回答时看不到前面聊过什么 —— R6 补齐这一段。
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

        /**
         * 渲染成 prompt 用的「对话历史」正文。
         *
         * <p>格式：
         * <pre>
         * 【历史摘要】
         * · 讨论过离心机使用前检查项与配平要求
         *
         * 【最近对话】
         * 用户：离心机使用前要检查什么
         * 助手：- 电源指示灯是否正常 [1] …
         * </pre>
         *
         * <p><b>没有历史时返回空串</b> —— 上层据此完全不渲染该段，
         * 保证单轮问答的 prompt 与"无记忆版"逐字一致（保护既有 30 条评估不回退）。
         */
        public String historyText() {
            boolean hasSummary = summary != null && !summary.isBlank();
            boolean hasTurns = recentTurns != null && !recentTurns.isEmpty();
            if (!hasSummary && !hasTurns) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            if (hasSummary) {
                sb.append("【历史摘要】\n").append(summary.trim()).append("\n");
            }
            if (hasTurns) {
                if (hasSummary) {
                    sb.append('\n');
                }
                sb.append("【最近对话】\n");
                for (RagMessage m : recentTurns) {
                    sb.append(roleLabel(m.getRole())).append("：")
                            .append(oneLine(m.getContent())).append('\n');
                }
            }
            return sb.toString();
        }

        /**
         * 历史给意图分类用的极简版（只要 USER 问句，省 token）。
         *
         * <p>意图分类需要知道"用户在延续哪个话题"，助手答句是次要信息。
         */
        public String userQuestionDigest() {
            if (recentTurns == null || recentTurns.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (RagMessage m : recentTurns) {
                if ("USER".equals(m.getRole())) {
                    sb.append("用户：").append(oneLine(m.getContent())).append('\n');
                }
            }
            return sb.toString();
        }

        private static String roleLabel(String role) {
            if (role == null) {
                return "用户";
            }
            return switch (role) {
                case "USER" -> "用户";
                case "ASSISTANT" -> "助手";
                case "SYSTEM" -> "系统";
                default -> role;
            };
        }

        /** 压成单行（历史进 prompt 时不换行，避免结构混乱） */
        private static String oneLine(String s) {
            if (s == null) {
                return "";
            }
            String flat = s.replaceAll("\\s*[\\r\\n]+\\s*", " ").trim();
            return flat.length() <= 400 ? flat : flat.substring(0, 400) + "…";
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

        // 多取一些：摘要覆盖范围之外的消息要能被 SessionSummarizer 拿到做压缩，
        // 这里只把"摘要之后"的留在 recentTurns 里。
        List<RagMessage> fetched = messageMapper.listRecent(session.getId(), messageLimit * 3);
        if (fetched == null || fetched.isEmpty()) {
            fetched = Collections.emptyList();
        } else {
            Collections.reverse(fetched);   // 时间正序
        }

        // 已进摘要的消息不再重复进 prompt
        Long summaryUpTo = session.getSummaryUpTo();
        List<RagMessage> recent = new java.util.ArrayList<>();
        for (RagMessage m : fetched) {
            if (summaryUpTo != null && m.getId() != null && m.getId() <= summaryUpTo) {
                continue;
            }
            recent.add(m);
            if (recent.size() >= messageLimit) {
                break;
            }
        }

        // Token 预算裁剪（摘要优先保留，最近对话从最老开始丢）
        int budget = props.getChat().getMaxContextTokens();
        String summary = session.getSummary();
        int summaryTokens = estimateTokens(summary);
        List<RagMessage> trimmed = trimToBudget(recent, Math.max(0, budget - summaryTokens));

        log.debug("[RAG][CTX] sessionId={} 取回={} 条(去重后 {}) → 预算内 {} 条 (摘要 {} token, 预算={})",
                session.getId(), fetched.size(), recent.size(), trimmed.size(), summaryTokens, budget);

        return new LoadedContext(session, summary, trimmed);
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