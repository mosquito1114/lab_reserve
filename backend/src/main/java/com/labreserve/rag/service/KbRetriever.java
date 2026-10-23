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
import com.labreserve.rag.util.Bm25Encoder;
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
 * <p>R4 起走 <b>Hybrid 双路检索</b>：dense（语义）+ sparse（BM25 关键词），
 * 由 Qdrant 服务端用 {@code Fusion.RRF} 融合（见 {@code QdrantAdapter.hybridSearch}）。
 * R3 是单路 dense。
 *
 * <p>只搜 {@code status=1}（已启用）的文档，DISABLED 的自然不返回。
 *
 * <h3>未命中硬拒（rag-design §12.6）</h3>
 * Qdrant 返回空 或 top1 融合分 &lt; {@code chat.kb-min-score} → 返回空列表，
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

    @Autowired
    private RelevanceJudge relevanceJudge;

    /**
     * 检索知识库。
     *
     * @param query           重写后的检索问句
     * @param topK            返回条数
     * @return 引用列表（按 score 降序）；空列表 = 未命中 → 上层硬拒
     */
    public RetrievalResult retrieve(String query, int topK) {
        // ① 把问句 embed 成查询向量（dense 路）
        float[] queryVector;
        try {
            queryVector = embeddingClient.embed(query);
        } catch (Exception e) {
            log.error("[RAG][KB] 查询向量生成失败: {}", e.getMessage());
            throw new BizException(500, "查询向量生成失败: " + e.getMessage());
        }

        // ② 同时把问句编码成 BM25 稀疏向量（sparse 路）
        Bm25Encoder.SparseVector sparse = Bm25Encoder.encode(query);

        // ③ 相关性门禁（在调 LLM 之前先把"库里到底有没有"判掉）
        //
        //    ★ 关键设计：RRF 分是**排名分**（1/(60+rank)），量级 ~0.016~0.033，
        //      它只能表达"谁排前面"，无法表达"是否真的相关"——
        //      即使库里完全没相关内容，也会有东西排第一拿 0.016 分。
        //      所以阈值判断必须用 **dense 的 cosine 分**（绝对量，阈值 0.35 有意义），
        //      RRF 只负责排序（融合），不参与命中判定。
        //
        //    ★ 进一步（R4 实证）：小语料下 cosine 区分度仍不够——
        //      负样本 0.4638 夹在正样本 0.4508~0.7121 中间，单一阈值分不开。
        //      所以采用三段式：高分放行 / 低分拒 / 灰区调 LLM 判相关性
        //      （见 RelevanceJudge）。

        //   3.1 dense 门禁分（取 top1 cosine，不设阈值先拿到分）
        List<QdrantAdapter.ScoredChunk> denseGate =
                qdrantAdapter.searchDense(qdrantAdapter.collectionName(), queryVector, 1, 0.0d);
        double gateScore = denseGate.isEmpty() ? 0.0d : denseGate.get(0).score();
        log.info("[RAG][KB] dense 门禁分: {} query={}", round(gateScore), abbreviate(query));

        //   3.2 dense 分数过低 → 用 BM25 兼底（型号/编号/术语类的字面精确匹配）
        if (gateScore < props.getChat().getKbRejectScore()) {
            List<QdrantAdapter.ScoredChunk> sparseGate =
                    qdrantAdapter.searchSparse(qdrantAdapter.collectionName(),
                            sparse.indices(), sparse.values(), 1);
            if (sparseGate.isEmpty()) {
                log.info("[RAG][KB] dense({}) < 下限({}) 且无 BM25 命中 → 硬拒: query={}",
                        round(gateScore), props.getChat().getKbRejectScore(), abbreviate(query));
                return RetrievalResult.empty();
            }
            log.info("[RAG][KB] dense 未达下限但 BM25 命中，继续走 hybrid: query={} sparseScore={}",
                    abbreviate(query), round(sparseGate.get(0).score()));
        }

        // ④ Hybrid 排序：dense + sparse 双路 → Qdrant 服务端 RRF 融合
        //    prefetchK 取 topK 的 3 倍，给 RRF 足够候选（只取 topK 会让融合退化为单路）
        int prefetchK = topK * Math.max(1, props.getChat().getKbPrefetchFactor());
        List<QdrantAdapter.ScoredChunk> hits = qdrantAdapter.hybridSearch(
                qdrantAdapter.collectionName(), queryVector,
                sparse.indices(), sparse.values(), topK, prefetchK);

        if (hits.isEmpty()) {
            log.info("[RAG][KB] hybrid 返回空: query={} sparseTerms={}",
                    abbreviate(query), sparse.size());
            return RetrievalResult.empty();
        }

        // ⑤ 反查 chunk 原文 + document 标题 → 组装引用
        List<RagChunk> hitChunks = new ArrayList<>(hits.size());
        List<CitationVO> citations = toCitations(hits, hitChunks);
        if (citations.isEmpty()) {
            return RetrievalResult.empty();
        }

        // ⑥ 三段式相关性仲裁（R4 核心）
        //    灰区调 LLM 判「证据是否足以回答」，避免小语料下 cosine 误放行
        //    ★ 必须传完整 chunk 正文给判官（不是 UI 用的 200 字 snippet），
        //      否则答案在 chunk 中段/尾段时会被误判为不相关（联调踩过）
        List<String> evidenceTexts = hitChunks.stream().map(RagChunk::getContent).toList();
        RelevanceJudge.Verdict verdict =
                relevanceJudge.judge(query, gateScore, evidenceTexts, citations);
        if (!verdict.relevant()) {
            log.info("[RAG][KB] 相关性仲裁未通过({}) → 硬拒: query={}",
                    verdict.reason(), abbreviate(query));
            return RetrievalResult.empty();
        }

        return new RetrievalResult(citations, evidenceTexts);
    }

    /**
     * 把 Qdrant 命中点转成引用列表（含原文片段与标题）。
     */
    private List<CitationVO> toCitations(List<QdrantAdapter.ScoredChunk> hits,
                                        List<RagChunk> outChunks) {
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
            outChunks.add(chunk);
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

    /**
     * 检索结果。
     *
     * <p><b>为什么要分 citations 与 evidenceTexts</b>：
     * {@link CitationVO#getSnippet()} 是给 UI 展示用的 200 字短片段，
     * 但合成器 / 相关性判官需要<b>完整 chunk 正文</b>才能看到位于中段/尾段的答案。
     * 两处混用会导致"检索对了但答不出来"（R4 联调踩过两次）。
     *
     * @param citations     引用列表（含 200 字 snippet，用于前端展示与角标）
     * @param evidenceTexts 与 citations 一一对应的<b>完整</b> chunk 正文（供 LLM 使用）
     */
    public record RetrievalResult(List<CitationVO> citations, List<String> evidenceTexts) {
        public boolean isEmpty() {
            return citations == null || citations.isEmpty();
        }

        public static RetrievalResult empty() {
            return new RetrievalResult(List.of(), List.of());
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