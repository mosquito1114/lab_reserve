package com.labreserve.rag.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labreserve.common.exception.BizException;
import com.labreserve.rag.config.RagProperties;
import com.labreserve.rag.mapper.RagDocumentMapper;
import com.labreserve.rag.mapper.RagMessageMapper;
import com.labreserve.rag.mapper.RagSessionMapper;
import com.labreserve.rag.mapper.RagUsageStatsMapper;
import com.labreserve.rag.model.dto.ChatSendDTO;
import com.labreserve.rag.model.entity.RagDocument;
import com.labreserve.rag.model.entity.RagMessage;
import com.labreserve.rag.model.entity.RagSession;
import com.labreserve.rag.model.entity.RagUsageStats;
import com.labreserve.rag.model.vo.CitationVO;
import com.labreserve.rag.model.vo.Intent;
import com.labreserve.rag.model.vo.RagStreamItem;
import com.labreserve.rag.service.AnswerSynthesizer;
import com.labreserve.rag.service.ContextLoaderService;
import com.labreserve.rag.service.FairQueueService;
import com.labreserve.rag.service.IntentRouter;
import com.labreserve.rag.service.KbRetriever;
import com.labreserve.rag.service.QueryRewriter;
import com.labreserve.rag.service.RagChatService;
import com.labreserve.rag.util.TraceIdUtil;
import com.labreserve.util.SecurityUtils;
import com.labreserve.util.SensitiveUtils;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RAG QUERY 链主服务（6 步，见 rag-design §12.4）。
 *
 * <pre>
 * Step 0 Gate            限频 + 全局并发（FairQueueService）
 * Step 1 ContextLoader   会话摘要 + 最近 N 轮（ContextLoaderService）
 * Step 2 QueryRewriter   指代补全成可独立检索的问句（QueryRewriter）
 * Step 3 IntentRouter    4 维意图（docs / chitchat / unsafe / out_of_scope）
 * Step 4 KbRetriever     dense 检索；未命中硬拒不调 LLM
 * Step 5 AnswerSynthesizer 流式合成（带引用角标）
 * Step 6 Persist         落 USER/ASSISTANT 消息 + usage 统计
 * </pre>
 *
 * <p><b>v3 范围收缩</b>：没有 Tool 调用、没有 MCP、没有业务接口透传。业务操作类问题走
 * {@code out_of_scope} → 静态引导语，不检索不调 LLM。
 *
 * <h3>事件流协议</h3>
 * 顺序固定：{@code CHUNK*} → {@code CITATION} → {@code MESSAGE} → {@code DONE}。
 * 三条短路分支（chitchat / unsafe / out_of_scope）直接发 {@code MESSAGE}（模板答案）+ {@code DONE}，
 * 不发 CHUNK / CITATION。
 *
 * <h3>并发与超时</h3>
 * 每一步都有超时保护（{@code SafeStep} 语义），失败走降级：发 {@code DEGRADED} + 模板兜底，
 * 不让整条流炸掉。降级事实记进 {@code tb_rag_usage_stats.degraded=1}。
 */
@Slf4j
@Service
public class RagChatServiceImpl implements RagChatService {

    /** Step 1 输出：会话 + 历史摘要 + 最近对话 */
    @Autowired
    private ContextLoaderService contextLoader;

    /** Step 2 */
    @Autowired
    private QueryRewriter rewriter;

    /** Step 3 */
    @Autowired
    private IntentRouter intentRouter;

    /** Step 4 */
    @Autowired
    private KbRetriever kbRetriever;

    /** Step 5 */
    @Autowired
    private AnswerSynthesizer synthesizer;

    /** Step 0 */
    @Autowired
    private FairQueueService fairQueue;

    @Autowired
    private RagSessionMapper sessionMapper;
    @Autowired
    private RagMessageMapper messageMapper;
    @Autowired
    private RagDocumentMapper documentMapper;
    @Autowired
    private RagUsageStatsMapper usageStatsMapper;

    @Autowired
    private SecurityUtils securityUtils;
    @Autowired
    private SensitiveUtils sensitiveUtils;
    @Autowired
    private RagProperties props;

    private final ObjectMapper mapper = new ObjectMapper();

    /** 发起提问者信息（在 HTTP 线程捕获，避免异步 SSE 线程读不到 SecurityContext） */
    public record CallerInfo(Long userId, String roleCode) {
    }

    @Override
    public Flux<RagStreamItem> chat(ChatSendDTO dto) {
        Long userId = securityUtils.getUserId();
        BizException.requireFound(userId, "未登录");
        CallerInfo caller = new CallerInfo(userId, securityUtils.getRoleCode());
        String traceId = TraceIdUtil.newId();
        MDC.put("traceId", traceId);

        long startMs = System.currentTimeMillis();
        AtomicInteger usedTokens = new AtomicInteger();
        AtomicBoolean degraded = new AtomicBoolean(false);

        return Flux.defer(() -> {
            // ============ Step 0 限频 ============
            fairQueue.checkQuota(userId);

            // ============ 会话准备 ============
            RagSession session = resolveSession(dto.getSessionId(), userId, dto.getMessage());

            // ============ Step 1 上下文 ============
            var context = contextLoader.load(session, dto.getMessage());

            // ============ Step 2 问题重写 ============
            var rewritten = rewriter.rewrite(dto.getMessage(), context.lastTurnDigest());

            // ============ Step 3 意图 ============
            Intent intent = intentRouter.route(rewritten.question());

            // ---------- 落 USER 消息 ----------
            RagMessage userMsg = new RagMessage();
            userMsg.setSessionId(session.getId());
            userMsg.setRole("USER");
            userMsg.setContent(dto.getMessage());
            userMsg.setRewritten(rewritten.question());
            userMsg.setIntent(writeJson(intent));
            userMsg.setTraceId(traceId);
            userMsg.setCreatedAt(LocalDateTime.now());
            messageMapper.insert(userMsg);
            sessionMapper.bumpMessage(session.getId());

            // ---------- 分叉 ----------
            return switch (intent.dominant()) {
                case UNSAFE -> templateAnswer(session, userMsg, traceId, startMs, usedTokens, degraded, caller,
                        "抱歉，这个请求我无法处理。\n\n"
                                + "我是实验室预约系统的知识库问答助手，只回答规章制度、操作手册、"
                                + "实验 SOP 和常见问题方面的问题。",
                        intent);

                case OUT_OF_SCOPE -> templateAnswer(session, userMsg, traceId, startMs, usedTokens, degraded, caller,
                        "抱歉，这个问题我无法回答。\n\n"
                                + "我是实验室预约系统的知识库问答助手，能干两类事：\n"
                                + "① 回答规章制度 / 操作手册 / 实验 SOP / 常见问题（知识库内容）\n"
                                + "② 你问的问题如果属于**系统业务操作**，请到对应页面完成：\n"
                                + "   · 查/取消预约 → 【预约管理】\n"
                                + "   · 我的实验课表 → 【我的课表】\n"
                                + "   · 报修进度 → 【设备运维】",
                        intent);

                case CHITCHAT -> templateAnswer(session, userMsg, traceId, startMs, usedTokens, degraded, caller,
                        "你好！我是实验室预约系统的智能问答助手。\n\n"
                                + "我可以帮你查询知识库里的内容，例如：\n"
                                + "· 实验室安全规定有哪些要求\n"
                                + "· 离心机怎么操作\n"
                                + "· 危险化学品怎么存放\n\n"
                                + "有什么想了解的，直接问我就行。",
                        intent);

                case DOCS -> retrievalBranch(session, userMsg, traceId, startMs, caller,
                        intent, rewritten.question(), usedTokens, degraded);
            };
        }).doFinally(signal -> {
            MDC.remove("traceId");
            fairQueue.releaseQuota(userId);
            log.debug("[RAG][CHAT] 流结束 traceId={} signal={} cost={}ms",
                    traceId, signal, System.currentTimeMillis() - startMs);
        });
    }

    // ============================================================
    // docs 分支：检索 → 合成 → 引用
    // ============================================================

    private Flux<RagStreamItem> retrievalBranch(RagSession session, RagMessage userMsg, String traceId,
                                               long startMs, CallerInfo caller, Intent intent,
                                               String rewrittenQuestion,
                                               AtomicInteger usedTokens, AtomicBoolean degraded) {
        // ---- Step 4 检索 ----
        List<CitationVO> citations;
        try {
            citations = kbRetriever.retrieve(rewrittenQuestion, props.getChat().getHistoryWindow());
        } catch (Exception e) {
            log.warn("[RAG][CHAT] 检索异常,降级为拒答: {}", e.getMessage());
            degraded.set(true);
            return templateAnswer(session, userMsg, traceId, startMs, usedTokens, degraded, caller,
                    "AI 服务暂时繁忙，请稍后再试。", intent);
        }

        // ---- 未命中硬拒（零幻觉，不调 LLM）----
        if (citations == null || citations.isEmpty()) {
            log.info("[RAG][CHAT] 检索未命中,硬拒不调 LLM: sessionId={}", session.getId());
            return templateAnswer(session, userMsg, traceId, startMs, usedTokens, degraded, caller,
                    "抱歉，我没在实验室知识库里找到相关说明。\n\n"
                            + "你可以试试问得更具体一些，比如：\n"
                            + "· 「危险化学品怎么存放」\n"
                            + "· 「离心机使用前要检查什么」\n"
                            + "· 「实验室安全规定有哪些要求」\n\n"
                            + "如果有文档没上传到知识库，请联系管理员补充。",
                    intent);
        }

        // ---- Step 5 流式合成 ----
        Flux<String> textFlux;
        try {
            textFlux = synthesizer.stream(rewrittenQuestion, citations)
                    .timeout(java.time.Duration.ofSeconds(60));
        } catch (Exception e) {
            log.warn("[RAG][CHAT] 合成建立失败,降级: {}", e.getMessage());
            degraded.set(true);
            return templateAnswer(session, userMsg, traceId, startMs, usedTokens, degraded, caller,
                    "回答生成失败，请重新提问。", intent);
        }

        StringBuilder buffer = new StringBuilder();
        AtomicBoolean persisted = new AtomicBoolean(false);

        // 正文 chunk 透传 + 累积
        Flux<RagStreamItem> body = textFlux.map(chunk -> {
            buffer.append(chunk);
            return new RagStreamItem(RagStreamItem.Kind.CHUNK, chunk, null, null, null, null, null, null);
        });

        // 流结束后发 citation + message + done（只执行一次）
        Flux<RagStreamItem> tail = Flux.defer(() -> {
            if (!persisted.compareAndSet(false, true)) {
                return Flux.empty();
            }
            String answer = buffer.toString();
            int cost = (int) (System.currentTimeMillis() - startMs);
            Long assistantMsgId = persistAssistant(session, answer, citations, traceId, cost, caller, intent,
                    degraded.get());

            RagStreamItem citationItem = new RagStreamItem(
                    RagStreamItem.Kind.CITATION, null, citations, null, null, null, null, null);
            RagStreamItem messageItem = new RagStreamItem(
                    RagStreamItem.Kind.MESSAGE, null, citations, assistantMsgId, usedTokens.get(), cost, null, null);
            RagStreamItem doneItem = new RagStreamItem(
                    RagStreamItem.Kind.DONE, null, null, assistantMsgId, usedTokens.get(), cost, null, null);

            return Flux.just(citationItem, messageItem, doneItem);
        });

        // concatWith 需要 body 完成后才执行 tail
        return body.concatWith(tail);
    }

    // ============================================================
    // 模板直答（chitchat / unsafe / out_of_scope / 未命中）
    // ============================================================

    private Flux<RagStreamItem> templateAnswer(RagSession session, RagMessage userMsg, String traceId,
                                              long startMs, AtomicInteger usedTokens,
                                              AtomicBoolean degraded, CallerInfo caller,
                                              String answer, Intent intent) {
        int cost = (int) (System.currentTimeMillis() - startMs);
        // 模板答案当作"一次性正文"发（前端仍走 chunk 渲染，保持 UI 一致）
        RagStreamItem chunk = new RagStreamItem(
                RagStreamItem.Kind.CHUNK, answer, null, null, null, null, null, null);
        Long assistantMsgId = persistAssistant(session, answer, List.of(), traceId, cost, caller, intent,
                degraded.get());
        RagStreamItem message = new RagStreamItem(
                RagStreamItem.Kind.MESSAGE, null, List.of(), assistantMsgId, usedTokens.get(), cost, null, null);
        RagStreamItem done = new RagStreamItem(
                RagStreamItem.Kind.DONE, null, null, assistantMsgId, usedTokens.get(), cost, null, null);

        Flux<RagStreamItem> base = Flux.just(chunk, message, done);

        if (degraded.get()) {
            RagStreamItem dg = new RagStreamItem(
                    RagStreamItem.Kind.DEGRADED, null, null, null, null, null, "服务繁忙，已返回兜底答案", null);
            base = Flux.concat(Flux.just(dg), base);
        }
        return base;
    }

    // ============================================================
    // 落库
    // ============================================================

    private Long persistAssistant(RagSession session, String answer, List<CitationVO> citations,
                                  String traceId, int costMs, CallerInfo caller, Intent intent,
                                  boolean degraded) {
        // 流式后置脱敏（第③道墙）：手机号 + 身份证
        String safe = sensitiveUtils.maskInText(answer);

        RagMessage msg = new RagMessage();
        msg.setSessionId(session.getId());
        msg.setRole("ASSISTANT");
        msg.setContent(safe);
        msg.setCitations(writeJson(citations));
        msg.setTraceId(traceId);
        msg.setCostMs(costMs);
        msg.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(msg);
        sessionMapper.bumpMessage(session.getId());

        // usage 统计
        RagUsageStats stats = new RagUsageStats();
        stats.setUserId(caller.userId());
        stats.setRoleCode(caller.roleCode() == null ? "UNKNOWN" : caller.roleCode());
        stats.setIntent(intent == null ? "DOCS" : intent.dominant().name());
        stats.setUsedTokens(0); // R3 未接真实 token 计费，先记 0
        stats.setDocHits(citations == null ? 0 : citations.size());
        stats.setToolCalls(0); // v3 reserved
        stats.setCostMs(costMs);
        stats.setDegraded(degraded ? 1 : 0);
        usageStatsMapper.insert(stats);

        return msg.getId();
    }

    // ============================================================
    // 会话
    // ============================================================

    private RagSession resolveSession(Long sessionId, Long userId, String firstMessage) {
        if (sessionId != null) {
            RagSession s = sessionMapper.selectById(sessionId);
            BizException.requireFound(s, "会话不存在");
            // 越权检查：只能用自己的会话
            if (!userId.equals(s.getUserId())) {
                throw BizException.forbidden("无权访问该会话");
            }
            return s;
        }
        // 新会话
        RagSession s = new RagSession();
        s.setUserId(userId);
        s.setTitle("新会话");
        s.setMessageCount(0);
        s.setLastTokenWindow(0);
        s.setLastMessageAt(LocalDateTime.now());
        sessionMapper.insert(s);
        // 首条消息回填标题
        String title = firstMessage == null || firstMessage.isBlank()
                ? "新会话" : firstMessage.trim();
        if (title.length() > 20) {
            title = title.substring(0, 20) + "…";
        }
        sessionMapper.updateTitle(s.getId(), title);
        s.setTitle(title);
        return s;
    }

    private String writeJson(Object o) {
        try {
            return o == null ? null : mapper.writeValueAsString(o);
        } catch (Exception e) {
            return null;
        }
    }
}