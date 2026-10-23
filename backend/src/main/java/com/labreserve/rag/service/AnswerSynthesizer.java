package com.labreserve.rag.service;

import com.labreserve.rag.model.vo.CitationVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Step 5 · AnswerSynthesizer（答复合成）。
 *
 * <p>v3 安全三道墙之 ①②（见 rag-design §12.7）：
 * <ol>
 *   <li><b>系统 Prompt 强约束</b>：明确"只依据【证据】回答；不知道就说不知道；
 *       业务操作类问题引导到对应页面"</li>
 *   <li><b>输出 Schema 强约束</b>：正文里的具体事实（日期/编号/参数）必须挂 {@code [n]} 角标，
 *       角标只能引用 {@link CitationVO#index()} 里出现过的编号</li>
 * </ol>
 *
 * <p>第③道墙（流式后置脱敏）在 {@code RagChatServiceImpl} 落库前做。
 *
 * <h3>流式</h3>
 * 调 {@code ChatModel.stream(Prompt)}（{@code DashScopeChatModel} 解析 SSE），
 * 逐 chunk 吐给前端。本类不落库、不组装最终 message——那是 Step 6 的事。
 */
@Slf4j
@Service
public class AnswerSynthesizer {

    /** 系统 Prompt：人设 + 三条硬规则（安全墙①②） */
    private static final String SYSTEM = """
            你是「实验室预约与资源管理系统」的智能问答助手。

            你的能力边界：**只回答实验室知识库（规章制度 / 操作手册 / 实验 SOP / 常见问题）里的内容**。

            规则：
            1) 只依据【证据】回答，不要编造。证据里没有的信息，一律回答"知识库里没有相关内容"。
            2) 具体事实（数值、步骤编号、参数、时间）后面必须挂引用角标 [n]。
               **n 只能是【证据】列表里出现的序号（1、2、3…）**，
               严禁使用章节号（如 [8.2]、[3.1]）或自造编号 —— 章节号在正文里请直接用文字表述。
            3) 如果用户问的是"我自己的某条业务记录"或要求你代为操作（取消预约、报修进度等），
               说明这类操作请到对应系统页面完成，不要尝试代替用户操作。
            4) 用中文回答，简洁、直接，不要复述我的问题。
            """;

    @Autowired
    private ChatClient primaryLlm;

    /**
     * 流式合成答复。
     *
     * @param question   重写后的检索问句（用户听到的自然语言版本）
     * @param citations  检索命中的引用（可能为空——上层已在未命中时短路，不会走到这里）
     * @return 正文 chunk 流（增量文本）
     */
    public Flux<String> stream(String question, List<CitationVO> citations, List<String> evidenceTexts) {
        String evidence = renderEvidence(citations, evidenceTexts);
        String userText = """
                【证据】
                %s

                【问题】
                %s

                请依据上面的证据回答，事实后面挂 [n] 角标。
                """.formatted(evidence, question);

        Prompt prompt = Prompt.builder()
                .messages(new SystemMessage(SYSTEM), new UserMessage(userText))
                .build();

        log.info("[RAG][SYNTH] 流式合成: question={} evidenceChunks={}",
                abbreviate(question), citations.size());

        return primaryLlm.prompt()
                .messages(prompt.getInstructions())
                .stream()
                .content();
    }

    /**
     * 把引用渲染成带编号的证据文本。
     *
     * <p>每条格式：{@code [1] 《标题》(类别) 片段原文…}
     */
    /**
     * 把引用渲染成带编号的证据文本。
     *
     * <p>每条格式：{@code [1] 《标题》(类别) + 完整正文}。
     * 优先用 {@code evidenceTexts}（完整 chunk 正文）；缺失时退回 200 字 snippet。
     *
     * <p><b>为什么必须用完整正文</b>：snippet 只有 200 字，
     * 而答案常位于 chunk 中段/尾段（如"易燃易爆存放"在 chunk 第 2 章）。
     * 只给 snippet 会让 LLM 误答"知识库里没有相关内容"——R4 联调踩过。
     */
    private String renderEvidence(List<CitationVO> citations, List<String> evidenceTexts) {
        if (citations == null || citations.isEmpty()) {
            return "（无）";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < citations.size(); i++) {
            CitationVO c = citations.get(i);
            String body;
            if (evidenceTexts != null && i < evidenceTexts.size()
                    && evidenceTexts.get(i) != null && !evidenceTexts.get(i).isBlank()) {
                body = evidenceTexts.get(i);
            } else {
                body = c.getSnippet() == null ? "" : c.getSnippet();
            }
            // 单片上限 2000 字：防止超长 chunk 撑爆 prompt（正常 chunk <= 750 字）
            if (body.length() > 2000) {
                body = body.substring(0, 2000) + "…";
            }
            sb.append("[").append(c.getIndex()).append("] 《")
                    .append(c.getTitle() == null ? "未命名文档" : c.getTitle())
                    .append("》(")
                    .append(c.getCategory() == null ? "-" : c.getCategory())
                    .append(")\n")
                    .append(body)
                    .append("\n\n");
        }
        return sb.toString();
    }

    private static String abbreviate(String s) {
        if (s == null) return "";
        return s.length() <= 40 ? s : s.substring(0, 40) + "…";
    }
}