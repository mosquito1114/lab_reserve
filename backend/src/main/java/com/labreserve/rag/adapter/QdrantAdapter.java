package com.labreserve.rag.adapter;

import com.labreserve.common.exception.BizException;
import com.labreserve.rag.config.RagProperties;
import io.qdrant.client.PointIdFactory;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.ValueFactory;
import io.qdrant.client.VectorFactory;
import io.qdrant.client.VectorsFactory;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.Common;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Qdrant 1.x 适配器。
 *
 * <p>封装 R2 写入侧三个动作：
 * <ul>
 *   <li>{@link #initCollectionIfNeeded()} — 启动期 lazy 创建 collection (dense 1024 + sparse BM25)
 *       + payload index (status / category / document_id)</li>
 *   <li>{@link #upsertPoints(String, List, int)} — UpsertStep 用，写 points</li>
 *   <li>{@link #deleteByDocVersion(String, int)} — reingest 时清旧 points</li>
 * </ul>
 *
 * <h3>集合 schema (R2 RAG 单一集合 {@code lab_reserve_docs})</h3>
 * <ul>
 *   <li><b>dense</b>: dense 1024 维 COSINE —— text-embedding-v3 @ DashScope 默认就是 1024 维</li>
 *   <li><b>sparse</b>: BM25 稀疏向量 —— Qdrant 原生支持, R3 QUERY 端会消费 hybrid 路径</li>
 *   <li><b>payload</b>: status / category / document_id 三个 keyword/integer 索引</li>
 * </ul>
 *
 * <p>读取侧（搜索 / hybrid）留给 R3 的 {@code RetrievalPort}，本类暂不实现。
 *
 * <h3>Point ID 策略</h3>
 * RAG 的 chunk 用 {@code UUID.nameUUIDFromBytes("doc-{docId}-v{ver}-{idx}")} 生成确定性 UUID：
 * <ul>
 *   <li>同一 (docId, version, idx) 重跑 = 同一 UUID → Qdrant upsert 幂等</li>
 *   <li>不暴露数据库自增 ID；UUID v5 算法保证全局唯一</li>
 *   <li>用 {@link #pointIdString} 序列化保持可读性，便于排查</li>
 * </ul>
 *
 * @author lab_reserve
 */
@Slf4j
@Component
public class QdrantAdapter {

    @Autowired
    private QdrantClient client;

    @Autowired
    private RagProperties props;

    @PostConstruct
    void postConstruct() {
        // 启动期 lazy 触发一次 init;失败不阻断 boot,留给 Step 首次失败抛
        try {
            initCollectionIfNeeded();
        } catch (Exception e) {
            log.warn("[RAG][Qdrant] 启动期 collection 初始化失败,将在首次 EMBED Step 触发重试: {}",
                    e.getMessage());
        }
    }

    // ------------------------------------------------------------
    // Collection 生命周期
    // ------------------------------------------------------------

    /**
     * 检查 collection 是否存在;不存在则按 RAG 配置创建(dense + sparse + payload index)。
     * 幂等: 多次调用安全。
     */
    public void initCollectionIfNeeded() {
        String collection = collectionName();
        try {
            Boolean exists = await(client.collectionExistsAsync(collection), Duration.ofSeconds(10));
            if (Boolean.TRUE.equals(exists)) {
                log.info("[RAG][Qdrant] collection 已存在: {}", collection);
                return;
            }

            log.info("[RAG][Qdrant] 创建 collection: {} (dense={} cosine, sparse=BM25)",
                    collection, props.getIngest().getVectorSize());

            // named dense vector: "dense"
            Collections.VectorParams denseParams = Collections.VectorParams.newBuilder()
                    .setSize(props.getIngest().getVectorSize())
                    .setDistance(Collections.Distance.Cosine)
                    .build();
            Collections.VectorParamsMap denseMap = Collections.VectorParamsMap.newBuilder()
                    .putAllMap(java.util.Map.of("dense", denseParams))
                    .build();

            // sparse named vector: "sparse" (BM25 索引, R3 QUERY 端消费)
            // Qdrant 1.x 的 sparse 配置位于 sparseVectorsConfig,不在 named vectors 里
            Collections.SparseVectorParams sparseParams = Collections.SparseVectorParams.newBuilder().build();
            Collections.SparseVectorConfig sparseConfig = Collections.SparseVectorConfig.newBuilder()
                    .putAllMap(java.util.Map.of("sparse", sparseParams))
                    .build();

            // 用 CreateCollection builder 直接组装 (overload 走原生 builder 路径)
            Collections.CreateCollection createReq = Collections.CreateCollection.newBuilder()
                    .setCollectionName(collection)
                    .setVectorsConfig(Collections.VectorsConfig.newBuilder()
                            .setParamsMap(denseMap)
                            .build())
                    .setSparseVectorsConfig(sparseConfig)
                    .build();

            await(client.createCollectionAsync(createReq, Duration.ofSeconds(30)), Duration.ofSeconds(60));

            // payload indexes: status / category / document_id
            await(client.createPayloadIndexAsync(collection, "status",
                    Collections.PayloadSchemaType.Keyword, null, true, null,
                    Duration.ofSeconds(10)), Duration.ofSeconds(15));
            await(client.createPayloadIndexAsync(collection, "category",
                    Collections.PayloadSchemaType.Keyword, null, true, null,
                    Duration.ofSeconds(10)), Duration.ofSeconds(15));
            await(client.createPayloadIndexAsync(collection, "document_id",
                    Collections.PayloadSchemaType.Integer, null, true, null,
                    Duration.ofSeconds(10)), Duration.ofSeconds(15));

            log.info("[RAG][Qdrant] collection {} 创建完成, payload indexes 就绪", collection);
        } catch (Exception e) {
            throw new BizException(500, "Qdrant 集合初始化失败: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------
    // 数据写入
    // ------------------------------------------------------------

    /**
     * 把 points 推到 Qdrant collection 的 dense 分量(sparse 本 R2 不写, R3 检索前同步补)。
     *
     * @param collection 集合名
     * @param jobs       {@link PointsUpsertJob} 列表,内含 pointId / dense vector / payload
     */
    public void upsertPoints(String collection, List<PointsUpsertJob> jobs) {
        if (jobs == null || jobs.isEmpty()) {
            return;
        }
        try {
            List<Points.PointStruct> structs = jobs.stream()
                    .map(PointsUpsertJob::toPointStruct)
                    .toList();
            Points.UpdateResult result = await(
                    client.upsertAsync(collection, structs),
                    Duration.ofSeconds(60));
            log.info("[RAG][Qdrant] upsert 完成: collection={} count={} status={}",
                    collection, jobs.size(), result.getStatus());
        } catch (Exception e) {
            throw new BizException(500, "Qdrant upsert 失败: " + e.getMessage());
        }
    }

    /**
     * 按 docId + ingestVersion 删除点 (reingest 时清理老版本,只留新版)。
     *
     * <p>Qdrant 的 {@code deleteAsync(collection, Filter)} 使用 {@code Common.Filter}
     * 表达式锁定 (document_id = X AND ingest_version = Y)。
     */
    public void deleteByDocVersion(String collection, Long documentId, Integer ingestVersion) {
        try {
            Common.Filter filter = Common.Filter.newBuilder()
                    .addMust(Common.Condition.newBuilder()
                            .setField(Common.FieldCondition.newBuilder()
                                    .setKey("document_id")
                                    .setMatch(Common.Match.newBuilder().setInteger(documentId).build())
                                    .build())
                            .build())
                    .addMust(Common.Condition.newBuilder()
                            .setField(Common.FieldCondition.newBuilder()
                                    .setKey("ingest_version")
                                    .setMatch(Common.Match.newBuilder().setInteger(ingestVersion).build())
                                    .build())
                            .build())
                    .build();
            Points.UpdateResult result = await(
                    client.deleteAsync(collection, filter),
                    Duration.ofSeconds(30));
            log.info("[RAG][Qdrant] deleteByDocVersion: collection={} docId={} v={} status={}",
                    collection, documentId, ingestVersion, result.getStatus());
        } catch (Exception e) {
            log.warn("[RAG][Qdrant] deleteByDocVersion 失败 collection={} docId={} v={} err={}",
                    collection, documentId, ingestVersion, e.getMessage());
        }
    }

    // ------------------------------------------------------------
    // 数据读取（R3 QUERY 链消费）
    // ------------------------------------------------------------

    /**
     * dense 向量检索（QUERY 链 Step 4 的主路径）。
     *
     * <p>只搜 {@code status=1}（已启用）的文档，DISABLED 的文档自然不返回。
     *
     * @param collection     集合名
     * @param queryVector    查询向量（1024 维）
     * @param topK           返回条数
     * @param scoreThreshold 分数阈值，低于此分不返回（R3 硬拒判定用；传 0 表示不过滤）
     * @return 命中的点（含 payload）
     */
    public List<ScoredChunk> searchDense(String collection, float[] queryVector,
                                         int topK, double scoreThreshold) {
        try {
            Points.SearchPoints request = Points.SearchPoints.newBuilder()
                    .setCollectionName(collection)
                    .setVectorName("dense")
                    .addAllVector(com.google.common.primitives.Floats.asList(queryVector))
                    .setLimit(topK)
                    .setScoreThreshold((float) scoreThreshold)
                    .setWithPayload(Points.WithPayloadSelector.newBuilder().setEnable(true).build())
                    .setFilter(enabledDocsFilter())
                    .build();

            List<Points.ScoredPoint> results = await(
                    client.searchAsync(request, Duration.ofSeconds(30)), Duration.ofSeconds(45));

            List<ScoredChunk> out = new ArrayList<>(results.size());
            for (Points.ScoredPoint sp : results) {
                Map<String, Object> payload = new HashMap<>();
                sp.getPayloadMap().forEach((k, v) -> payload.put(k, fromJsonValue(v)));
                out.add(new ScoredChunk(sp.getId().getUuid(), sp.getScore(), payload));
            }
            log.debug("[RAG][Qdrant] dense 检索: collection={} topK={} 命中={}",
                    collection, topK, out.size());
            return out;
        } catch (Exception e) {
            throw new BizException(500, "Qdrant dense 检索失败: " + e.getMessage());
        }
    }

    /**
     * 构造"只搜启用文档"的过滤条件（{@code status=1}）。
     */
    private static Common.Filter enabledDocsFilter() {
        return Common.Filter.newBuilder()
                .addMust(Common.Condition.newBuilder()
                        .setField(Common.FieldCondition.newBuilder()
                                .setKey("status")
                                .setMatch(Common.Match.newBuilder().setInteger(1L).build())
                                .build())
                        .build())
                .build();
    }

    /** JsonWithInt.Value → Java Object */
    private static Object fromJsonValue(JsonWithInt.Value v) {
        return switch (v.getKindCase()) {
            case STRING_VALUE -> v.getStringValue();
            case INTEGER_VALUE -> v.getIntegerValue();
            case DOUBLE_VALUE -> v.getDoubleValue();
            case BOOL_VALUE -> v.getBoolValue();
            default -> null;
        };
    }

    /**
     * 检索命中的点。
     *
     * @param pointId Qdrant point UUID
     * @param score   相似度分数（cosine 越大越近）
     * @param payload 点 payload（document_id / category / chunk_index 等）
     */
    public record ScoredChunk(String pointId, double score, Map<String, Object> payload) {
    }

    // ------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------

    /** 集合名读自 {@link RagProperties.Ingest#getCollection()} */
    public String collectionName() {
        return props.getIngest().getCollection();
    }

    /**
     * 把 (documentId, ingestVersion, chunkIndex) 转成确定性 UUID。
     * 重跑 = 同一 UUID = Qdrant upsert 自然幂等。
     */
    public static UUID pointIdUuid(Long documentId, Integer ingestVersion, Integer chunkIndex) {
        String seed = "doc-" + documentId + "-v" + ingestVersion + "-" + chunkIndex;
        return UUID.nameUUIDFromBytes(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** 同上但带额外 suffix (reingest 之后老 points 清理可用) */
    public static String pointIdString(Long documentId, Integer ingestVersion, Integer chunkIndex) {
        return "doc-" + documentId + "-v" + ingestVersion + "-" + chunkIndex;
    }

    /**
     * ListenableFuture 的等待 + 异常翻译。
     *
     * @param future 异步 future
     * @param timeout gRPC 调用超时
     */
    private static <T> T await(com.google.common.util.concurrent.ListenableFuture<T> future,
                                Duration timeout) {
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new BizException(500, "Qdrant 调用被中断");
        } catch (TimeoutException te) {
            throw new BizException(500, "Qdrant 调用超时 (" + timeout.toMillis() + "ms)");
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause() != null ? ee.getCause() : ee;
            throw new BizException(500, "Qdrant 调用失败: " + cause.getMessage());
        }
    }

    // ------------------------------------------------------------
    // 入参结构
    // ------------------------------------------------------------

    /**
     * Upsert 任务: Qdrant 点写入的最小数据单元。
     * 由 {@code UpsertStep} 构造,送进 {@link #upsertPoints}。
     */
    public record PointsUpsertJob(
            UUID pointId,
            float[] denseVector,
            Map<String, Object> payload
    ) {
        public Points.PointStruct toPointStruct() {
            Map<String, Points.Vector> vectors = new HashMap<>();
            vectors.put("dense", VectorFactory.vector(denseVector));
            // sparse 暂不写,R3 QUERY 时再补(否则需要在本服务做 BM25 encoder)

            // payload: 统一转成 JsonWithInt.Value
            Map<String, JsonWithInt.Value> payloadValues = new HashMap<>();
            if (payload != null) {
                payload.forEach((k, v) -> payloadValues.put(k, toJsonValue(v)));
            }

            return Points.PointStruct.newBuilder()
                    .setId(PointIdFactory.id(pointId))
                    .setVectors(VectorsFactory.namedVectors(vectors))
                    .putAllPayload(payloadValues)
                    .build();
        }

        private static JsonWithInt.Value toJsonValue(Object v) {
            if (v == null) {
                return ValueFactory.nullValue();
            }
            if (v instanceof Boolean b) {
                return ValueFactory.value(b);
            }
            if (v instanceof Integer i) {
                return ValueFactory.value(i.longValue());
            }
            if (v instanceof Long l) {
                return ValueFactory.value(l);
            }
            if (v instanceof Double d) {
                return ValueFactory.value(d);
            }
            if (v instanceof Float f) {
                return ValueFactory.value(f.doubleValue());
            }
            // 字符串 / 其它 一律走 string
            return ValueFactory.value(v.toString());
        }
    }
}
