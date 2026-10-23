package com.labreserve.rag.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labreserve.common.exception.BizException;
import com.labreserve.rag.config.RagProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * DashScope Embedding 客户端。
 *
 * <p>绕开 Spring AI 1.0 GA 的 {@code OpenAiEmbeddingModel}——后者与 DashScope
 * "OpenAI 兼容模式" 在 content-type 协商上有冲突（实测 Spring 把
 * {@code application/json} 响应判为 {@code application/octet-stream}，
 * 导致 {@code EmbeddingList<Embedding>} 反序列化抛
 * {@code RestClientException}）。
 *
 * <p>本类直接用 JDK 11+ {@code HttpClient} 调 DashScope embedding endpoint:
 * <pre>{@code
 *   POST {base-url}/embeddings
 *   Authorization: Bearer {api-key}
 *   Content-Type: application/json
 *
 *   { "model": "text-embedding-v3", "input": [...], "encoding_format": "float" }
 * }</pre>
 * 响应是标准 OpenAI embedding JSON。手写 Jackson 解析。
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>{@link #embed(String)} 单文本 → {@code float[]}</li>
 *   <li>{@link #embed(List)} 批量 → {@code List<float[]>}</li>
 *   <li>向量维度由 DashScope 返回, 调用方校验与 {@code vector-size} 一致</li>
 * </ul>
 *
 * <h3>配置</h3>
 * <pre>
 *   lab-reserve.rag.llm.base-url       = https://dashscope.aliyuncs.com/compatible-mode/v1
 *   lab-reserve.rag.llm.api-key        = sk-...
 *   lab-reserve.rag.llm.embedding-model = text-embedding-v3
 *   lab-reserve.rag.llm.timeout-seconds = 30
 * </pre>
 *
 * @author lab_reserve
 */
@Slf4j
@Component
public class DashScopeEmbeddingClient {

    /**
     * DashScope embedding 接口的硬限制：单次请求 {@code input} 最多 10 条。
     * 超过会返回 400 InvalidParameter（"batch size is invalid, it should not be larger than 10"）。
     *
     * <p>这里在客户端内部再兜一层：调用方传多少都行，本类自己拆成 ≤10 的批次。
     * 这样即使 application.yml 或文档级配置写大了，也不会让整条摄取链路失败。
     */
    private static final int MAX_BATCH = 10;

    @Autowired
    private RagProperties props;

    private HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @PostConstruct
    void init() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        log.info("[RAG][Embedding] 初始化: base={} model={} dimensions={}",
                props.getLlm().getBaseUrl(),
                props.getLlm().getEmbeddingModel(),
                props.getIngest().getVectorSize());
    }

    /**
     * 批量嵌入。
     *
     * @param inputs 待嵌入文本列表
     * @return 长度 = inputs 的 List，每项 float[] 长度为 DashScope 返回的 dim
     * @throws BizException 调 LLM 失败 / 维度不匹配
     */
    public List<float[]> embed(List<String> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return List.of();
        }
        // 超过 API 上限就自动拆批，按原顺序拼回结果
        if (inputs.size() > MAX_BATCH) {
            List<float[]> all = new ArrayList<>(inputs.size());
            for (int i = 0; i < inputs.size(); i += MAX_BATCH) {
                int end = Math.min(i + MAX_BATCH, inputs.size());
                List<float[]> part = doEmbed(inputs.subList(i, end));
                if (part.size() != end - i) {
                    throw new BizException(500,
                            "DashScope embedding 分批结果数量不一致（批次 " + i + "-" + end + "）");
                }
                all.addAll(part);
            }
            return all;
        }
        return doEmbed(inputs);
    }

    /** 真正发一次 HTTP 请求（入参长度必须 ≤ {@link #MAX_BATCH}） */
    private List<float[]> doEmbed(List<String> inputs) {
        try {
            // 构造 request body
            var node = mapper.createObjectNode();
            node.put("model", props.getLlm().getEmbeddingModel());
            var inputArray = node.putArray("input");
            for (String s : inputs) {
                inputArray.add(s);
            }
            node.put("encoding_format", "float");
            String body = mapper.writeValueAsString(node);

            URI uri = URI.create(stripTrailingSlash(props.getLlm().getBaseUrl()) + "/embeddings");
            HttpRequest req = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(props.getLlm().getTimeoutSeconds()))
                    .header("Authorization", "Bearer " + props.getLlm().getApiKey())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            int status = resp.statusCode();
            if (status != 200) {
                throw new BizException(500, "DashScope embedding 调用失败: HTTP " + status
                        + " body=" + truncate(resp.body(), 200));
            }

            return parseEmbeddingResponse(resp.body());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[RAG][Embedding] HTTP 调用异常: {}", e.getMessage());
            throw new BizException(500, "DashScope embedding 调用异常: " + e.getMessage());
        }
    }

    /** 单文本便捷包装 */
    public float[] embed(String text) {
        return embed(List.of(text)).get(0);
    }

    // ------------------------------------------------------------
    // 解析
    // ------------------------------------------------------------

    /**
     * 解析 DashScope 返回的 OpenAI 标准格式:
     * <pre>{@code
     * {
     *   "data": [{"embedding": [...], "index": 0, "object": "embedding"}, ...],
     *   "model": "text-embedding-v3",
     *   "object": "list",
     *   "usage": {"prompt_tokens": N, "total_tokens": N}
     * }
     * }</pre>
     */
    private List<float[]> parseEmbeddingResponse(String json) throws Exception {
        JsonNode root = mapper.readTree(json);
        JsonNode data = root.path("data");
        if (!data.isArray() || data.isEmpty()) {
            throw new BizException(500, "DashScope embedding 响应缺 data 数组");
        }
        List<float[]> result = new ArrayList<>(data.size());
        for (JsonNode item : data) {
            int index = item.path("index").asInt(result.size());
            JsonNode vec = item.path("embedding");
            if (!vec.isArray()) {
                throw new BizException(500, "DashScope embedding 响应第 " + index + " 项缺 embedding 数组");
            }
            float[] arr = new float[vec.size()];
            for (int i = 0; i < vec.size(); i++) {
                arr[i] = (float) vec.get(i).asDouble();
            }
            result.add(index, arr);
        }
        return result;
    }

    private static String stripTrailingSlash(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
