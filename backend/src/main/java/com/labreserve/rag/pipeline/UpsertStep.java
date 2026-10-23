package com.labreserve.rag.pipeline;

import com.labreserve.common.exception.BizException;
import com.labreserve.rag.adapter.QdrantAdapter;
import com.labreserve.rag.enums.RagPipelineState;
import com.labreserve.rag.enums.StepCode;
import com.labreserve.rag.mapper.RagChunkMapper;
import com.labreserve.rag.model.entity.RagChunk;
import com.labreserve.rag.model.entity.RagDocument;
import com.labreserve.rag.util.Bm25Encoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * UPSERT Step（design.md §三 / §R2）。
 *
 * <h3>状态机</h3>
 * <pre>
 *   EMBEDDED → UPSERTING → UPSERTED
 * </pre>
 *
 * <h3>流程</h3>
 * <ol>
 *   <li>取同 (docId, version) 的 chunks，从 {@code vector_blob} 反序列化向量</li>
 *   <li>先 {@link QdrantAdapter#deleteByDocVersion} 清掉同 (docId, version) 老 points（幂等）</li>
 *   <li>组装 {@link QdrantAdapter.PointsUpsertJob} 列表 (pointId UUID 来自 (docId, ver, idx))</li>
 *   <li>{@link QdrantAdapter#upsertPoints} 推到 Qdrant</li>
 *   <li>回写 {@code tb_rag_chunk.vector_ref} = "doc-{id}-v{ver}-{idx}" 便于反向追溯</li>
 * </ol>
 *
 * <h3>sparse</h3>
 * 本步只写 dense；sparse 部分 R3 接入 BM25 encoder 后补（design.md §R2/R3 衔接）。
 *
 * <h3>幂等性</h3>
 * delete + upsert 同 (docId, version) 是天然幂等的；
 * reingest 时本步被调两次（每次新 version），老 points 由 deleteByDocVersion 清掉。
 */
@Slf4j
@Component
public class UpsertStep extends AbstractPipelineStep {

    @Autowired
    private QdrantAdapter qdrantAdapter;

    @Autowired
    private RagChunkMapper chunkMapper;

    @Override
    public StepCode code() {
        return StepCode.UPSERT;
    }

    @Override
    protected String fromState() {
        return RagPipelineState.EMBEDDED.name();
    }

    @Override
    protected String inState() {
        return RagPipelineState.UPSERTING.name();
    }

    @Override
    protected String successState() {
        return RagPipelineState.UPSERTED.name();
    }

    @Override
    protected StepResult apply(StepContext ctx) {
        RagDocument doc = getDocument(ctx.documentId());
        if (doc == null) {
            throw BizException.notFound("文档不存在: " + ctx.documentId());
        }
        String collection = qdrantAdapter.collectionName();

        List<RagChunk> chunks = chunkMapper.listByDocVersion(doc.getId(), ctx.ingestVersion());
        if (chunks.isEmpty()) {
            throw new BizException(500, "EMBED Step 之前未产出 chunks");
        }
        // 防御: 所有 chunks 必须有 vector_blob
        for (RagChunk c : chunks) {
            if (c.getVectorBlob() == null || c.getVectorBlob().length == 0) {
                throw new BizException(500, "chunk " + c.getId() + " 缺少 vector_blob,请先跑 EMBED");
            }
        }

        // 1. 先清掉同 (docId, version) 的老 points (幂等)
        qdrantAdapter.deleteByDocVersion(collection, doc.getId(), ctx.ingestVersion());

        // 2. 组装 upsert 任务
        List<QdrantAdapter.PointsUpsertJob> jobs = new ArrayList<>(chunks.size());
        for (RagChunk c : chunks) {
            float[] vec = EmbedStep.fromBytesLE(c.getVectorBlob());
            UUID pointId = QdrantAdapter.pointIdUuid(doc.getId(), ctx.ingestVersion(), c.getChunkIndex());
            String vectorRef = QdrantAdapter.pointIdString(doc.getId(), ctx.ingestVersion(), c.getChunkIndex());

            Map<String, Object> payload = new HashMap<>(8);
            payload.put("document_id", doc.getId());
            payload.put("ingest_version", ctx.ingestVersion());
            payload.put("chunk_index", c.getChunkIndex());
            payload.put("category", doc.getCategory());
            payload.put("source_type", doc.getSourceType());
            payload.put("status", doc.getStatus() == null ? 0 : doc.getStatus());

            // sparse（BM25）: 对切片正文编码，服务端用 Idf 修饰子加权
            var sparse = Bm25Encoder.encode(c.getContent());

            jobs.add(new QdrantAdapter.PointsUpsertJob(pointId, vec,
                    sparse.indices(), sparse.values(), payload));
        }

        // 3. 推 Qdrant
        qdrantAdapter.upsertPoints(collection, jobs);

        // 4. 回写 vector_ref 到 chunks
        for (RagChunk c : chunks) {
            c.setVectorRef(QdrantAdapter.pointIdString(doc.getId(), ctx.ingestVersion(), c.getChunkIndex()));
            chunkMapper.updateById(c);
        }

        log.info("[RAG][UPSERT] 完成 docId={} v={} chunks={} collection={} (dense+sparse)",
                doc.getId(), ctx.ingestVersion(), chunks.size(), collection);

        return StepResult.ok()
                .put("collection", collection)
                .put("chunkCount", chunks.size())
                .put("vectorSize", chunks.get(0).getVectorBlob().length / 4)
                .put("hybrid", true);
    }
}
