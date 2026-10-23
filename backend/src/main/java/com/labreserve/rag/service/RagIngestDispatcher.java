package com.labreserve.rag.service;

import com.labreserve.common.constant.RedisKeyConst;
import com.labreserve.rag.config.RagAsyncConfig;
import com.labreserve.rag.pipeline.AbstractPipelineStep;
import com.labreserve.rag.pipeline.ChunkStep;
import com.labreserve.rag.pipeline.EmbedStep;
import com.labreserve.rag.pipeline.ExtractStep;
import com.labreserve.rag.pipeline.FinalizeStep;
import com.labreserve.rag.pipeline.SanitizeStep;
import com.labreserve.rag.pipeline.StepContext;
import com.labreserve.rag.pipeline.TraceContext;
import com.labreserve.rag.pipeline.UpsertStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 摄取调度器（{@code @Async("ragIngestExecutor")}）。
 *
 * <p>由 {@link RagDocumentService} 在 uploadAndDispatch 后调一次；它走独立线程池，
 * 不阻塞 HTTP 响应。链式调用 4 个 Step（R1 截止到 CHUNK）：
 * <ol>
 *   <li>{@link ExtractStep} —— OSS → Tika → rawText</li>
 *   <li>{@link SanitizeStep} —— rawText → cleanText（敏感脱敏）</li>
 *   <li>{@link ChunkStep} —— cleanText → tb_rag_chunk</li>
 * </ol>
 *
 * <p>R2 起在 CHUNK 后接 EMBED → UPSERT → FINALIZE → READY。本 R1 截止到 CHUNKED。
 *
 * <p>单步失败由 {@link AbstractPipelineStep} 写 FAILED + last_error_* 后抛 BizException；
 * 调度器只打日志，不重试——R5 的 {@code RecoveryRunner} 接管后续续跑。
 *
 * <p>traceId 沿链透传：每个 Step 共用同一个 StepContext（同一 traceId），便于日志 / step_row 串联。
 *
 * @see RagAsyncConfig#INGEST_EXECUTOR
 */
@Slf4j
@Component
public class RagIngestDispatcher {

    @Autowired
    private ExtractStep extractStep;

    @Autowired
    private SanitizeStep sanitizeStep;

    @Autowired
    private ChunkStep chunkStep;

    @Autowired
    private EmbedStep embedStep;

    @Autowired
    private UpsertStep upsertStep;

    @Autowired
    private FinalizeStep finalizeStep;

    @Autowired
    private TraceContext traceContext;

    /**
     * 全链重跑（首次 dispatch 与 reingest 入口都走这里）。
     *
     * @param documentId   文档主键
     * @param ingestVersion 目标 ingest 版本号
     */
    @Async(RagAsyncConfig.INGEST_EXECUTOR)
    public void runChain(Long documentId, Integer ingestVersion) {
        String traceId = traceContext.newId();
        StepContext ctx = StepContext.of(documentId, ingestVersion, traceId);

        log.info("[RAG] 启动摄取链 docId={} v={} traceId={}", documentId, ingestVersion, traceId);

        try {
            extractStep.execute(ctx);
            log.info("[RAG] EXTRACT 完成 docId={}", documentId);
            sanitizeStep.execute(ctx);
            log.info("[RAG] SANITIZE 完成 docId={}", documentId);
            chunkStep.execute(ctx);
            log.info("[RAG] CHUNK 完成 docId={} (状态机应到 CHUNKED)", documentId);
            embedStep.execute(ctx);
            log.info("[RAG] EMBED 完成 docId={} (状态机应到 EMBEDDED)", documentId);
            upsertStep.execute(ctx);
            log.info("[RAG] UPSERT 完成 docId={} (状态机应到 UPSERTED)", documentId);
            finalizeStep.execute(ctx);
            log.info("[RAG] FINALIZE 完成 docId={} (状态机应到 READY)", documentId);
        } catch (Exception e) {
            // 单步失败由 AbstractPipelineStep 已经写 FAILED + last_error_*
            // 这里只打最外层日志便于排查
            log.error("[RAG] 摄取链中止 docId={} v={} error={}",
                    documentId, ingestVersion, e.getMessage());
        }
    }

    /**
     * 单步手动重跑（{@code POST /api/rag/admin/documents/{id}/steps/{code}/run}）。
     * 与全链共用 ScheduleStep 锁（{@code RedisKeyConst.RAG_STEP_LOCK} 拼装规则相同）。
     *
     * @param ctx 调度器已经做好的 StepContext（含 traceId）
     * @param step 要跑的具体 Step Bean
     */
    public void runSingleStep(StepContext ctx, AbstractPipelineStep step) {
        log.info("[RAG] 单步重跑 docId={} v={} step={} traceId={}",
                ctx.documentId(), ctx.ingestVersion(), step.code(), ctx.traceId());
        try {
            // 不复用 runChain: 让手动重试的 traceId 由调用方管理
            step.execute(ctx);
        } catch (Exception e) {
            log.warn("[RAG] 单步重跑失败 docId={} step={}: {}",
                    ctx.documentId(), step.code(), e.getMessage());
        }
    }

    /**
     * 由调度器生成 traceId 时使用；暴露此方法便于 Controller 拿到同一个 traceId 回写 HTTP 响应。
     */
    public String newTraceId() {
        return traceContext.newId();
    }

    /**
     * 拼接 Step 锁 key（设计图 §3.7）：{@code lab:reserve:rag:step:{docId}:{ver}:{code}}
     * 暴露只是为了 AdminController 内部做断言，与 RedisKeyConst.ragStepLockKey 等价。
     */
    public static String stepLockKey(Long docId, Integer version, String code) {
        return RedisKeyConst.ragStepLockKey(docId, version, code);
    }
}
