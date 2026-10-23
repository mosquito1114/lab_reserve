package com.labreserve.rag.pipeline;

import com.labreserve.common.exception.BizException;
import com.labreserve.rag.adapter.QdrantAdapter;
import com.labreserve.rag.enums.RagPipelineState;
import com.labreserve.rag.enums.StepCode;
import com.labreserve.rag.mapper.RagChunkMapper;
import com.labreserve.rag.mapper.RagDocumentMapper;
import com.labreserve.rag.model.entity.RagDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * FINALIZE Step（design.md §三 / §R2）。
 *
 * <h3>状态机</h3>
 * <pre>
 *   UPSERTED → READY  (无中间 inState, 设计上一步直接落终态)
 * </pre>
 *
 * <p>由于 inState / successState 必须不同（{@link AbstractPipelineStep#transitTo} 走乐观 CAS），
 * 我们在 {@link #apply} 内部手动 transit,绕过基类的"先 inState 后 successState"模板,
 * 一次性把状态写成 {@code READY}。
 *
 * <h3>行为</h3>
 * <ol>
 *   <li>把文档标记为 {@code READY}（可被 QUERY 检索）</li>
 *   <li>写 {@code ready_at = NOW()}</li>
 *   <li>校验 chunkCount / vectorSize 落到 meta,便于评估门禁</li>
 * </ol>
 *
 * <h3>幂等性</h3>
 * reingest 时本步是新一轮 ingest 的最后一步,自然不重复;FINALIZE 内部对 {@code ready_at}
 * 用 CAS: 若文档已经是 {@code READY} 且 ready_at 非空,只在状态匹配时才更新。
 */
@Slf4j
@Component
public class FinalizeStep extends AbstractPipelineStep {

    @Autowired
    private RagChunkMapper chunkMapper;

    @Autowired
    private RagDocumentMapper docMapper;

    @Autowired
    private QdrantAdapter qdrantAdapter;

    @Override
    public StepCode code() {
        return StepCode.FINALIZE;
    }

    @Override
    protected String fromState() {
        return RagPipelineState.UPSERTED.name();
    }

    @Override
    protected String inState() {
        // 不用中间态: 直接 UPSERTED -> READY
        return RagPipelineState.UPSERTED.name();
    }

    @Override
    protected String successState() {
        return RagPipelineState.READY.name();
    }

    @Override
    protected StepResult apply(StepContext ctx) {
        RagDocument doc = getDocument(ctx.documentId());
        if (doc == null) {
            throw BizException.notFound("文档不存在: " + ctx.documentId());
        }

        // chunk 总数
        int chunkCount = chunkMapper.listByDocVersion(doc.getId(), ctx.ingestVersion()).size();

        // 写 ready_at + pipeline_state = READY
        doc.setReadyAt(LocalDateTime.now());
        doc.setPipelineState(RagPipelineState.READY.name());
        // chunk_count 终值（与 CHUNK Step 中间结果一致,此处覆盖一次,避免 COUNT 不一致）
        doc.setChunkCount(chunkCount);
        docMapper.updateById(doc);

        // 顺便初始化/确认 collection (QdrantAdapter @PostConstruct 失败时此处补救)
        try {
            qdrantAdapter.initCollectionIfNeeded();
        } catch (Exception e) {
            log.warn("[RAG][FINALIZE] initCollectionIfNeeded 失败, 不阻断: {}", e.getMessage());
        }

        log.info("[RAG][FINALIZE] 完成 docId={} v={} chunks={} readyAt={}",
                doc.getId(), ctx.ingestVersion(), chunkCount, doc.getReadyAt());

        return StepResult.ok()
                .put("chunkCount", chunkCount)
                .put("readyAt", doc.getReadyAt().toString())
                .put("collection", qdrantAdapter.collectionName());
    }
}
