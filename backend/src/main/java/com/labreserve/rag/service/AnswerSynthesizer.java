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
import java.util.stream.Collectors;

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
            2) 具体事实（数值、步骤编号、参数、时间）后面必须挂引用角标 [n]，n 只能取【证据】里出现过的编号。
            3) 如果用户问的是业务操作（查自己的预约、取消预约、报修进度等），说明这类操作请到对应系统页面完成，
               不要尝试代替用户操作。
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
    public Flux<String> stream(String question, List<CitationVO> citations) {
        String evidence = renderEvidence(citations);
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
    private String renderEvidence(List<CitationVO> citations) {
        if (citations == null || citations.isEmpty()) {
            return "（无）";
        }
        return citations.stream()
                .map(c -> "[%d] 《%s》(%s) %s".formatted(
                        c.getIndex(),
                        c.getTitle() == null ? "未命名文档" : c.getTitle(),
                        c.getCategory() == null ? "-" : c.getCategory(),
                        c.getSnippet() == null ? "" : c.getSnippet()))
                .collect(Collectors.joining("\n\n"));
    }

    private static String abbreviate(String s) {
        if (s == null) return "";
        return s.length() <= 40 ? s : s.substring(0, 40) + "…";
    }
}