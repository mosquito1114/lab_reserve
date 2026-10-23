package com.labreserve.rag.service;

import com.labreserve.common.exception.BizException;
import com.labreserve.rag.adapter.DashScopeEmbeddingClient;
import com.labreserve.rag.adapter.QdrantAdapter;
import com.labreserve.rag.config.RagProperties;
import com.labreserve.rag.mapper.RagChunkMapper;
import com.labreserve.rag.mapper.RagDocumentMapper;
import com.labreserve.rag.model.entity.RagChunk;
import com.labreserve.rag.model.entity.RagDocument;
import com.labreserve.rag.model.vo.CitationVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Step 4 · KbRetriever（知识库检索）。
 *
 * <p>R3 只做 <b>dense 向量检索</b>（RRF / BM25 sparse 融合放 R4，见 rag-design §12.8）。
 * Qdrant 侧只搜 {@code status=1} 的文档，DISABLED 的自然不返回。
 *
 * <h3>未命中硬拒（rag-design §12.6）</h3>
 * Qdrant 返回空 或 top1 分数 &lt; {@code chat.kb-min-score} → 返回空列表，
 * 上层<b>不调 LLM</b>，直接回"未在实验室知识库找到相关说明"。零幻觉。
 *
 * <p>检索结果按 {@code vectorRef}（{@code doc-{id}-v{ver}-{idx}}）反查
 * {@code tb_rag_chunk} 拿到引用片段原文，保证引用可追溯。
 */
@Slf4j
@Service
public class KbRetriever {

    @Autowired
    private DashScopeEmbeddingClient embeddingClient;

    @Autowired
    private QdrantAdapter qdrantAdapter;

    @Autowired
    private RagChunkMapper chunkMapper;

    @Autowired
    private RagDocumentMapper docMapper;

    @Autowired
    private RagProperties props;

    /**
     * 检索知识库。
     *
     * @param query           重写后的检索问句
     * @param topK            返回条数
     * @return 引用列表（按 score 降序）；空列表 = 未命中 → 上层硬拒
     */
    public List<CitationVO> retrieve(String query, int topK) {
        // ① 把问句 embed 成查询向量
        float[] queryVector;
        try {
            queryVector = embeddingClient.embed(query);
        } catch (Exception e) {
            log.error("[RAG][KB] 查询向量生成失败: {}", e.getMessage());
            throw new BizException(500, "查询向量生成失败: " + e.getMessage());
        }

        // ② dense 检索（阈值 0 拿全量，命中判定在上层做，便于统计"最相近的差多少"）
        List<QdrantAdapter.ScoredChunk> hits = qdrantAdapter.searchDense(
                qdrantAdapter.collectionName(), queryVector, topK, 0.0d);

        if (hits.isEmpty()) {
            log.info("[RAG][KB] 未命中任何 chunk: query={}", abbreviate(query));
            return List.of();
        }

        // ③ 硬拒判定：top1 分数低于阈值 → 视为未命中（零幻觉）
        double minScore = props.getChat().getKbMinScore();
        double top1 = hits.get(0).score();
        if (top1 < minScore) {
            log.info("[RAG][KB] top1 分数 {} < 阈值 {}，判定未命中: query={}",
                    round(top1), minScore, abbreviate(query));
            return List.of();
        }

        // ④ 反查 chunk 原文 + document 标题 → 组装引用
        return toCitations(hits);
    }

    /**
     * 把 Qdrant 命中点转成引用列表（含原文片段与标题）。
     */
    private List<CitationVO> toCitations(List<QdrantAdapter.ScoredChunk> hits) {
        List<CitationVO> citations = new ArrayList<>(hits.size());
        // 反查用的 documentId 集合（批量查标题，避免 N+1）
        Map<Long, RagDocument> docCache = new LinkedHashMap<>();
        int index = 1;

        for (QdrantAdapter.ScoredChunk hit : hits) {
            // payload 里取 document_id / chunk_index
            Long documentId = asLong(hit.payload().get("document_id"));
            Integer chunkIndex = asInt(hit.payload().get("chunk_index"));
            String category = hit.payload().get("category") == null
                    ? null : hit.payload().get("category").toString();

            if (documentId == null || chunkIndex == null) {
                // payload 异常（理论上不会，UPSERT 时一定写），跳过该点
                log.warn("[RAG][KB] 命中点 payload 缺 document_id/chunk_index: {}", hit.pointId());
                continue;
            }

            RagDocument doc = docCache.computeIfAbsent(documentId, id -> {
                RagDocument d = docMapper.selectById(id);
                return d == null ? new RagDocument() : d;
            });
            // 反查该文档该版本该序号的切片
            Integer version = asInt(hit.payload().get("ingest_version"));
            RagChunk chunk = findChunk(documentId, version, chunkIndex);
            if (chunk == null) {
                log.warn("[RAG][KB] 命中点找不到对应 chunk: docId={} v={} idx={}",
                        documentId, version, chunkIndex);
                continue;
            }

            CitationVO vo = new CitationVO();
            vo.setIndex(index++);
            vo.setDocumentId(documentId);
            vo.setTitle(doc.getTitle());
            vo.setCategory(category);
            vo.setChunkId(chunk.getId());
            vo.setVectorRef(chunk.getVectorRef());
            vo.setSnippet(truncate(chunk.getContent(), 200));
            vo.setScore(round(hit.score()));
            citations.add(vo);
        }
        return citations;
    }

    /**
     * 按 (docId, version, chunkIndex) 找切片。
     *
     * <p>一次查该 doc 该 version 的所有切片再在内存里按 index 匹配
     * （切片量在演示规模下很小，避免动态 SQL）。
     */
    private RagChunk findChunk(Long documentId, Integer version, int chunkIndex) {
        Integer v = version == null ? 1 : version;
        List<RagChunk> chunks = chunkMapper.listByDocVersion(documentId, v);
        return chunks.stream()
                .filter(c -> c.getChunkIndex() == chunkIndex)
                .findFirst()
                .orElse(null);
    }

    // ------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------

    private static Long asLong(Object o) {
        if (o == null) return null;
        if (o instanceof Long l) return l;
        if (o instanceof Integer i) return i.longValue();
        if (o instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer asInt(Object o) {
        if (o == null) return null;
        if (o instanceof Integer i) return i;
        if (o instanceof Long l) return l.intValue();
        if (o instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String abbreviate(String s) {
        if (s == null) return "";
        return s.length() <= 40 ? s : s.substring(0, 40) + "…";
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static double round(double d) {
        return Math.round(d * 10000.0) / 10000.0;
    }
}