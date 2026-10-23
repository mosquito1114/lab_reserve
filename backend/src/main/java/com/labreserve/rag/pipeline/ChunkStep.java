package com.labreserve.rag.pipeline;

import com.labreserve.common.exception.BizException;
import com.labreserve.rag.config.RagProperties;
import com.labreserve.rag.enums.RagPipelineState;
import com.labreserve.rag.enums.StepCode;
import com.labreserve.rag.mapper.RagChunkMapper;
import com.labreserve.rag.model.entity.RagChunk;
import com.labreserve.rag.model.entity.RagDocument;
import com.labreserve.rag.pipeline.chunk.ChunkingStrategy;
import com.labreserve.rag.pipeline.chunk.ChunkingStrategyRegistry;
import com.labreserve.rag.pipeline.chunk.RawChunk;
import com.labreserve.rag.pipeline.chunk.SplitContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * CHUNK Step（design.md §3.3 / §3.10）：读 cleanText → 切片 → 写 tb_rag_chunk。
 *
 * <h3>状态机</h3>
 * <pre>
 *   SANITIZED → CHUNKING → CHUNKED
 * </pre>
 *
 * <h3>三层回退（design.md §3.10.4）</h3>
 * <ul>
 *   <li>切片策略：{@code doc.chunkingStrategy} → {@link RagProperties.Ingest#getChunkDefault()} → "RECURSIVE"</li>
 *   <li>maxChars：{@code doc.chunkMaxChars} → {@link ChunkingStrategy#defaultMaxChars()} → {@code props.ingest.chunkMaxChars}</li>
 *   <li>overlap：{@code doc.chunkOverlap} → {@link ChunkingStrategy#defaultOverlap()} → {@code props.ingest.chunkOverlap}</li>
 * </ul>
 *
 * <h3>写库</h3>
 * <ol>
 *   <li>{@link RagChunkMapper#deleteByDocVersion} 清掉同 (docId, version) 旧切片——保证"重跑幂等"</li>
 *   <li>批量 INSERT 新切片</li>
 *   <li>UPDATE {@code tb_rag_document.chunk_count} = 切片数</li>
 * </ol>
 *
 * @see ChunkingStrategy
 */
@Slf4j
@Component
public class ChunkStep extends AbstractPipelineStep {

    @Autowired
    private RagProperties props;

    @Autowired
    private ChunkingStrategyRegistry strategyRegistry;

    @Autowired
    private RagChunkMapper chunkMapper;

    @Override
    public StepCode code() {
        return StepCode.CHUNK;
    }

    @Override
    protected String fromState() {
        return RagPipelineState.SANITIZED.name();
    }

    @Override
    protected String inState() {
        return RagPipelineState.CHUNKING.name();
    }

    @Override
    protected String successState() {
        return RagPipelineState.CHUNKED.name();
    }

    @Override
    protected StepResult apply(StepContext ctx) {
        RagDocument doc = getDocument(ctx.documentId());
        if (doc == null) {
            throw BizException.notFound("文档不存在: " + ctx.documentId());
        }
        String clean = doc.getCleanText();
        if (clean == null || clean.isBlank()) {
            throw new BizException(500, "SANITIZE 步骤未产出 clean_text");
        }

        // 三层回退：策略 + maxChars + overlap
        String strategyCode = resolveStrategyCode(doc);
        int maxChars = resolveMaxChars(strategyCode, doc);
        int overlap = resolveOverlap(strategyCode, doc);

        ChunkingStrategy strategy = strategyRegistry.get(strategyCode);
        SplitContext splitCtx = new SplitContext(
                maxChars, overlap, ctx.ingestVersion(), ctx.documentId(), doc.getCategory());

        log.info("[RAG][CHUNK] 开始切片 docId={} strategy={} maxChars={} overlap={} cleanLen={}",
                doc.getId(), strategyCode, maxChars, overlap, clean.length());

        List<RawChunk> rawChunks = strategy.split(clean, splitCtx);
        if (rawChunks.isEmpty()) {
            throw new BizException(500,
                    "切片结果为空（策略=" + strategyCode + "），可能是文档实际不含可切分的有效内容");
        }

        // 同 (docId, version) 老切片先删后插（R2 之后 chunks 也有 vector_ref，会同时清 Qdrant points）
        chunkMapper.deleteByDocVersion(doc.getId(), ctx.ingestVersion());
        List<RagChunk> rows = new ArrayList<>(rawChunks.size());
        for (int i = 0; i < rawChunks.size(); i++) {
            RawChunk rc = rawChunks.get(i);
            RagChunk row = new RagChunk();
            row.setDocumentId(doc.getId());
            row.setIngestVersion(ctx.ingestVersion());
            row.setChunkIndex(i);
            row.setContent(rc.content());
            row.setCharStart(rc.charStart());
            row.setCharEnd(rc.charEnd());
            row.setStrategyCode(strategyCode);
            // vectorRef 在 R2 EMBED/UPSERT 阶段写入
            row.setCreatedAt(LocalDateTime.now());
            rows.add(row);
        }
        // 单条 INSERT 是 MP 默认行为；批量改用 service 层自定义时 R5 引入 now()批次；R1 一条条插（量少）
        for (RagChunk row : rows) {
            chunkMapper.insert(row);
        }

        // 写 chunk_count 统计
        doc.setChunkCount(rows.size());
        docMapper.updateById(doc);

        // 统计
        int totalChars = 0;
        int maxChunkChars = 0;
        for (RawChunk rc : rawChunks) {
            int len = rc.content().length();
            totalChars += len;
            if (len > maxChunkChars) maxChunkChars = len;
        }

        return StepResult.ok()
                .put("strategy", strategyCode)
                .put("maxChars", maxChars)
                .put("overlap", overlap)
                .put("chunkCount", rows.size())
                .put("avgChars", rows.isEmpty() ? 0 : totalChars / rows.size())
                .put("maxChunkChars", maxChunkChars);
    }

    // --------------------------------------------------------------
    // 三层回退
    // --------------------------------------------------------------

    private String resolveStrategyCode(RagDocument doc) {
        if (doc.getChunkingStrategy() != null && !doc.getChunkingStrategy().isBlank()) {
            return doc.getChunkingStrategy().toUpperCase();
        }
        return props.getIngest().getChunkDefault();
    }

    private int resolveMaxChars(String strategyCode, RagDocument doc) {
        Integer docLevel = doc.getChunkMaxChars();
        if (docLevel != null && docLevel > 0) {
            return docLevel;
        }
        int strategyDefault = strategyRegistry.get(strategyCode).defaultMaxChars();
        if (strategyDefault > 0) {
            return strategyDefault;
        }
        return props.getIngest().getChunkMaxChars();
    }

    private int resolveOverlap(String strategyCode, RagDocument doc) {
        Integer docLevel = doc.getChunkOverlap();
        if (docLevel != null && docLevel >= 0) {
            return docLevel;
        }
        int strategyDefault = strategyRegistry.get(strategyCode).defaultOverlap();
        if (strategyDefault >= 0) {
            return strategyDefault;
        }
        return props.getIngest().getChunkOverlap();
    }
}
