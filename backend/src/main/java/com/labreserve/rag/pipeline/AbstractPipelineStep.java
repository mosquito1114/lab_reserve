package com.labreserve.rag.pipeline;

import com.labreserve.common.exception.BizException;
import com.labreserve.common.constant.RedisKeyConst;
import com.labreserve.rag.enums.StepCode;
import com.labreserve.rag.mapper.RagDocumentMapper;
import com.labreserve.rag.mapper.RagPipelineStepMapper;
import com.labreserve.rag.model.entity.RagDocument;
import com.labreserve.rag.model.entity.RagPipelineStep;
import com.labreserve.util.JsonUtils;
import com.labreserve.util.RedisLockTemplate;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

/**
 * 摄取管道 Step 模板方法基类（design.md §3.4）。
 *
 * <p>本类把"分布式锁 + 步骤行持久化 + 状态机推进 + traceId 注入"统一抽到
 * {@link #execute(StepContext)} 里；业务子类只用关心 {@link #apply(StepContext)}。
 *
 * <h3>执行流程</h3>
 * <pre>{@code
 * 1. acquire Redis lock by (docId, version, code)
 * 2. MDC.put("traceId", ctx.traceId())                  // 透传给 @Async 工作线程
 * 3. stepMapper.insert(row with status=1 RUNNING)
 * 4. docMapper.transitTo(docId, fromState(), inState()) // 状态机: 进入
 * 5. apply(ctx) -> StepResult                          // 业务子方法
 * 6a. SUCCESS 路径:
 *     - stepMapper.markSuccess(id, metaJson, costMs)
 *     - docMapper.transitTo(docId, inState(), successState())
 * 6b. FAILURE 路径 (BizException 或任意 Exception):
 *     - stepMapper.markFailed(id, errorMsg)
 *     - docMapper.markFailed(docId, code().name(), errorMsg)
 *     - 重抛原异常让调用方翻译为 HTTP
 * 7. finally: MDC.clear()
 * }</pre>
 *
 * <h3>并发语义</h3>
 * <ul>
 *   <li>同一 {@code (docId, ingestVersion, code)} 在任意时刻只跑一个 Step 实例</li>
 *   <li>{@code transitTo} 使用乐观 CAS（{@code WHERE pipeline_state = ?}）；并发时只一方推进</li>
 *   <li>手动 {@code POST /steps/{code}/run} 与自动全链走同一把锁 —— 不会出现"重跑冲突"</li>
 * </ul>
 *
 * <h3>异常处理</h3>
 * <ul>
 *   <li>{@link BizException}（业务预期）—— 状态写 FAILED + last_error_step，其余链路不下发</li>
 *   <li>{@link RuntimeException}（系统异常）—— 同上</li>
 *   <li>{@link Exception}（受检异常）—— 基类不显式接收，业务步骤把受检异常包成 BizException 即可</li>
 * </ul>
 *
 * @see StepCode
 */
@Slf4j
public abstract class AbstractPipelineStep {

    @Autowired
    protected RagDocumentMapper docMapper;

    @Autowired
    protected RagPipelineStepMapper stepMapper;

    @Autowired
    protected RedisLockTemplate redisLock;

    @Autowired
    protected TraceContext traceContext;

    @Autowired
    protected JsonUtils jsonUtils;

    // --------------------------------------------------------------
    // 业务步骤必须实现
    // --------------------------------------------------------------

    /** 当前步骤编码（用于 step_code 字段与 Redis 锁 key 拼装） */
    public abstract StepCode code();

    /** 当前步骤的"前置状态"——{@link #apply} 入口时文档应处于这个状态 */
    protected abstract String fromState();

    /** 当前步骤的"运行态"——{@link #apply} 期间文档处于这个状态（出现在 UI / 测试断言里） */
    protected abstract String inState();

    /** 当前步骤的成功落态——{@link #apply} 成功后文档转入这个状态 */
    protected abstract String successState();

    /**
     * 子类实现真正的业务逻辑（解析、清洗、切片、向量化、写 Qdrant...）。
     * 抛任何异常都会被基类捕获并落 FAILED，无需子类自己处理。
     *
     * @return success + 可选元数据；failure 时 {@link StepResult#fail(String)} 提供原因
     * @throws Exception 任意异常都会被基类捕获
     */
    protected abstract StepResult apply(StepContext ctx) throws Exception;

    // --------------------------------------------------------------
    // 模板
    // --------------------------------------------------------------

    /**
     * 模板入口（Spring Bean 暴露，调度器/Controller 调这个）。
     *
     * <p>本方法用 {@code final} 形式禁止覆写——一切 Step 共用同一流程。
     */
    public final void execute(StepContext ctx) {
        String lockKey = RedisKeyConst.ragStepLockKey(
                ctx.documentId(), ctx.ingestVersion(), code().name());
        String traceId = (ctx.traceId() == null || ctx.traceId().isBlank())
                ? traceContext.newId()
                : ctx.traceId();

        MDC.put("traceId", traceId);
        try {
            redisLock.execute(lockKey, 3, 60, () -> runLocked(ctx, traceId));
        } finally {
            MDC.remove("traceId");
        }
    }

    private Void runLocked(StepContext ctx, String traceId) {
        long stepStartMs = System.currentTimeMillis();
        // 1. RUNNING 行
        RagPipelineStep row = new RagPipelineStep();
        row.setDocumentId(ctx.documentId());
        row.setIngestVersion(ctx.ingestVersion());
        row.setStepCode(code().name());
        row.setStatus(1); // RUNNING
        row.setTraceId(traceId);
        row.setStartedAt(LocalDateTime.now());
        stepMapper.insert(row);

        try {
            // 2. 状态机推进：从 fromState() 到 inState()
            int transit = docMapper.transitTo(ctx.documentId(), fromState(), inState());
            if (transit == 0) {
                // 状态不匹配——典型场景：手动重试但文档已被人改过
                log.warn("[RAG][{}] 文档 {} 当前状态非 {}，放弃该步（已被 admin 改过？）",
                        code(), ctx.documentId(), fromState());
                stepMapper.markFailed(row.getId(),
                        "文档当前状态非前置态 " + fromState() + "，操作被取消");
                docMapper.markFailed(ctx.documentId(), code().name(),
                        "文档当前状态非前置态 " + fromState());
                return null;
            }

            // 3. 业务
            StepResult result = apply(ctx);

            int costMs = (int) (System.currentTimeMillis() - stepStartMs);

            // 4. 落库
            if (result.isSuccess()) {
                stepMapper.markSuccess(row.getId(), null,
                        jsonUtils.toJson(result.meta()), costMs);
                docMapper.transitTo(ctx.documentId(), inState(), successState());
                log.info("[RAG][{}] doc={} 成功 (cost={}ms, meta keys={})",
                        code(), ctx.documentId(), costMs, result.meta().size());
            } else {
                String msg = truncate(result.errorMsg(), 2000);
                stepMapper.markFailed(row.getId(), msg);
                docMapper.markFailed(ctx.documentId(), code().name(), msg);
                log.warn("[RAG][{}] doc={} 业务失败: {}",
                        code(), ctx.documentId(), msg);
                throw new BizException(500, "RAG step " + code() + " failed: " + msg);
            }
            return null;
        } catch (BizException e) {
            String msg = truncate(e.getMessage(), 2000);
            stepMapper.markFailed(row.getId(), msg);
            docMapper.markFailed(ctx.documentId(), code().name(), msg);
            log.warn("[RAG][{}] doc={} BizException: {}",
                    code(), ctx.documentId(), msg);
            throw e;
        } catch (RuntimeException e) {
            String msg = truncate(e.getMessage(), 2000);
            stepMapper.markFailed(row.getId(), msg);
            docMapper.markFailed(ctx.documentId(), code().name(), msg);
            log.error("[RAG][{}] doc={} RuntimeException", code(), ctx.documentId(), e);
            throw e;
        } catch (Exception e) {
            String msg = truncate(e.getMessage(), 2000);
            stepMapper.markFailed(row.getId(), msg);
            docMapper.markFailed(ctx.documentId(), code().name(), msg);
            log.error("[RAG][{}] doc={} Exception", code(), ctx.documentId(), e);
            throw new BizException(500, "RAG step " + code() + " failed: " + msg, e);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    /**
     * 给子类使用的查询帮助：拿当前文档。
     * <p>doc 表反冗余已写：业务上需要的字段不在这里 JOIN，由调用方拿 id 后再查 user / 等其它表。
     */
    protected RagDocument getDocument(Long id) {
        return docMapper.selectById(id);
    }
}
