package com.labreserve.rag.pipeline;

import com.labreserve.common.exception.BizException;
import com.labreserve.rag.adapter.DashScopeEmbeddingClient;
import com.labreserve.rag.config.RagProperties;
import com.labreserve.rag.enums.RagPipelineState;
import com.labreserve.rag.enums.StepCode;
import com.labreserve.rag.mapper.RagChunkMapper;
import com.labreserve.rag.model.entity.RagChunk;
import com.labreserve.rag.model.entity.RagDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * EMBED Step（design.md §三 / §R2）。
 *
 * <h3>状态机</h3>
 * <pre>
 *   CHUNKED → EMBEDDING → EMBEDDED
 * </pre>
 *
 * <h3>流程</h3>
 * <ol>
 *   <li>从 {@code tb_rag_chunk} 拿同 (docId, version) 的所有 chunk</li>
 *   <li>按 {@code embed-batch-size} 分批 (默认 16)，调 {@link EmbeddingModel#embed(List)} 拿向量</li>
 *   <li>断言向量维度 = {@code ingest.vector-size} (默认 1024，对应 DashScope text-embedding-v3)</li>
 *   <li>把向量转成 little-endian float 字节数组，写回 {@code vector_blob} 列</li>
 * </ol>
 *
 * <h3>幂等性</h3>
 * 同一 (docId, version) 重新跑时，已经写过 {@code vector_blob} 的 chunk 直接跳过；
 * 节省 Embedding API 配额。
 *
 * <h3>失败语义</h3>
 * - Embedding API 抛异常 → BizException → FAILED
 * - 向量维度不符 → BizException 强拒 (避免后续 UPSERT 维度冲突)
 */
@Slf4j
@Component
public class EmbedStep extends AbstractPipelineStep {

    @Autowired
    private RagProperties props;

    @Autowired
    private DashScopeEmbeddingClient embeddingClient;

    @Autowired
    private RagChunkMapper chunkMapper;

    @Override
    public StepCode code() {
        return StepCode.EMBED;
    }

    @Override
    protected String fromState() {
        return RagPipelineState.CHUNKED.name();
    }

    @Override
    protected String inState() {
        return RagPipelineState.EMBEDDING.name();
    }

    @Override
    protected String successState() {
        return RagPipelineState.EMBEDDED.name();
    }

    @Override
    protected StepResult apply(StepContext ctx) {
        RagDocument doc = getDocument(ctx.documentId());
        if (doc == null) {
            throw BizException.notFound("文档不存在: " + ctx.documentId());
        }
        int expectedDim = props.getIngest().getVectorSize();

        List<RagChunk> chunks = chunkMapper.listByDocVersion(doc.getId(), ctx.ingestVersion());
        if (chunks.isEmpty()) {
            throw new BizException(500, "CHUNK Step 未产出 chunks,EMBED 跑不下去");
        }

        int batchSize = Math.max(1, props.getIngest().getEmbedBatchSize());
        int totalTokensApprox = 0;  // 累加字数粗估,DashScope 按 token 收费时做参考
        int newEmbeds = 0;          // 本次新嵌入的 chunk 数(已存在的跳过)
        int reusedEmbeds = 0;        // 复用旧 vector_blob 的 chunk 数

        for (int i = 0; i < chunks.size(); i += batchSize) {
            int end = Math.min(i + batchSize, chunks.size());
            List<RagChunk> batch = chunks.subList(i, end);
            List<String> toEmbed = new ArrayList<>(batch.size());
            List<Integer> toEmbedIdx = new ArrayList<>(batch.size());
            for (int j = 0; j < batch.size(); j++) {
                RagChunk c = batch.get(j);
                totalTokensApprox += c.getContent().length();
                if (c.getVectorBlob() != null && c.getVectorBlob().length > 0) {
                    // 幂等: 已嵌入过的 chunk 跳过
                    reusedEmbeds++;
                    continue;
                }
                toEmbed.add(c.getContent());
                toEmbedIdx.add(j);
            }
            if (toEmbed.isEmpty()) {
                continue;
            }
            log.info("[RAG][EMBED] 批嵌入 docId={} batch {}-{} size={} (待嵌入={} 已嵌入={})",
                    doc.getId(), i, end, batch.size(), toEmbed.size(), batch.size() - toEmbed.size());
            List<float[]> embeddings = embeddingClient.embed(toEmbed);
            if (embeddings == null || embeddings.size() != toEmbed.size()) {
                throw new BizException(500,
                        "EMBED 调用结果数量(" + (embeddings == null ? 0 : embeddings.size())
                                + ") 与请求数量(" + toEmbed.size() + ") 不一致");
            }
            for (int k = 0; k < toEmbed.size(); k++) {
                float[] vec = embeddings.get(k);
                if (vec == null) {
                    throw new BizException(500, "EMBED 返回 null 向量");
                }
                if (vec.length != expectedDim) {
                    throw new BizException(500,
                            "EMBED 向量维度 " + vec.length + " 与配置 vector-size=" + expectedDim
                                    + " 不一致(模型与配置不匹配)");
                }
                RagChunk c = batch.get(toEmbedIdx.get(k));
                c.setVectorBlob(toBytesLE(vec));
                chunkMapper.updateById(c);
                newEmbeds++;
            }
        }

        log.info("[RAG][EMBED] 完成 docId={} total chunks={} 新嵌入={} 复用={} approxChars={}",
                doc.getId(), chunks.size(), newEmbeds, reusedEmbeds, totalTokensApprox);

        return StepResult.ok()
                .put("totalChunks", chunks.size())
                .put("newEmbeds", newEmbeds)
                .put("reusedEmbeds", reusedEmbeds)
                .put("approxChars", totalTokensApprox);
    }

    // --------------------------------------------------------------
    // float[] -> little-endian bytes
    // --------------------------------------------------------------

    /**
     * 把 float[] 转成 little-endian 字节流。
     * 后续 UPSERT 阶段会读回 float[] 再写进 Qdrant。
     */
    public static byte[] toBytesLE(float[] floats) {
        ByteBuffer bb = ByteBuffer.allocate(floats.length * Float.BYTES);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        FloatBuffer fb = bb.asFloatBuffer();
        fb.put(floats);
        return bb.array();
    }

    /** 反向: bytes (little-endian) -> float[] */
    public static float[] fromBytesLE(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return new float[0];
        }
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        FloatBuffer fb = bb.asFloatBuffer();
        float[] out = new float[fb.remaining()];
        fb.get(out);
        return out;
    }

    // 防 map 类型在编辑器里被警告(用不到, 防止 import 清掉)
    @SuppressWarnings("unused")
    private static <K, V> Map<K, V> noop() { return Map.of(); }
}
