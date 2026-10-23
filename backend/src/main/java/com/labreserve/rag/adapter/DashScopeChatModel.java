package com.labreserve.rag.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labreserve.common.exception.BizException;
import com.labreserve.rag.config.RagProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

/**
 * DashScope ChatModel —— 直接实现 Spring AI 的 {@link ChatModel} 接口。
 *
 * <p><b>为什么不直接用 Spring AI 的 {@code OpenAiChatModel}</b>：dev 分支的
 * {@code 4a63c80} 把 {@code spring.ai.model.chat} 默认设成 {@code none}，
 * 自动装配不创建 {@code ChatModel} Bean；等协作者手动把环境变量切成 {@code openai}
 * 又多一道外部依赖。本类自建 HTTP 客户端，<b>只要 {@code RAG_ENABLED=true}
 * 且 {@code LLM_API_KEY} 非空就能用</b>，与 {@link DashScopeEmbeddingClient} 同套路。
 *
 * <p>调用协议（OpenAI 兼容）：
 * <ul>
 *   <li>非流式 {@code POST {base}/chat/completions} → {@link #call(Prompt)}</li>
 *   <li>流式 {@code POST {base}/chat/completions}（{@code stream:true}）→ {@link #stream(Prompt)}</li>
 * </ul>
 *
 * <p><b>不支持 Tool Calling / 多模态</b>：本项目 v3 已取消 Tool Calling
 * （见 {@code docs/rag-design.md} §十二），只需要纯文本 chat。
 *
 * @author lab_reserve
 */
@Slf4j
@Component
public class DashScopeChatModel implements ChatModel {

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
        log.info("[RAG][ChatModel] 初始化: base={} model={} temp={} maxTokens={} timeout={}s",
                props.getLlm().getBaseUrl(),
                props.getLlm().getChatModel(),
                props.getLlm().getTemperature(),
                props.getLlm().getMaxTokens(),
                props.getLlm().getTimeoutSeconds());
    }

    // ============================================================
    // 非流式
    // ============================================================

    @Override
    public ChatResponse call(Prompt prompt) {
        Message[] messages = prompt.getInstructions().toArray(new Message[0]);
        try {
            URI uri = URI.create(base() + "/chat/completions");
            HttpRequest req = buildRequest(uri, messages, false);

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new BizException(500, "DashScope chat 调用失败: HTTP " + resp.statusCode()
                        + " body=" + truncate(resp.body(), 300));
            }

            JsonNode choices = mapper.readTree(resp.body()).path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new BizException(500, "DashScope chat 响应无 choices 数组");
            }
            String content = choices.get(0).path("message").path("content").asText("");
            if (content == null || content.isBlank()) {
                throw new BizException(500, "DashScope chat 响应 content 为空");
            }
            return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));

        } catch (BizException be) {
            throw be;
        } catch (Exception e) {
            log.error("[RAG][ChatModel] HTTP 调用异常: {}", e.getMessage());
            throw new BizException(500, "DashScope chat 调用异常: " + e.getMessage());
        }
    }

    // ============================================================
    // 流式（SSE）
    // ============================================================

    /**
     * 流式 chat。DashScope 返回 {@code data: {...}} 逐行 SSE，最后一行是 {@code data: [DONE]}。
     * 每收到一个含 {@code delta.content} 的 chunk 就 emit 一个 {@link ChatResponse}。
     */
    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        Message[] messages = prompt.getInstructions().toArray(new Message[0]);
        return Flux.<ChatResponse>defer(() -> {
            try {
                URI uri = URI.create(base() + "/chat/completions");
                HttpRequest req = buildRequest(uri, messages, true);

                HttpResponse<Stream<String>> resp = httpClient.send(
                        req, HttpResponse.BodyHandlers.ofLines());
                if (resp.statusCode() != 200) {
                    return Flux.error(new BizException(500,
                            "DashScope chat stream 失败: HTTP " + resp.statusCode()));
                }

                return Flux.fromStream(resp.body())
                        .map(String::trim)
                        .filter(line -> line.startsWith("data:"))
                        .map(line -> line.substring("data:".length()).trim())
                        .filter(payload -> !payload.isEmpty() && !"[DONE]".equals(payload))
                        .<ChatResponse>handle((payload, sink) -> {
                            try {
                                String content = mapper.readTree(payload)
                                        .path("choices").path(0)
                                        .path("delta").path("content").asText(null);
                                if (content != null && !content.isEmpty()) {
                                    sink.next(new ChatResponse(
                                            List.of(new Generation(new AssistantMessage(content)))));
                                }
                            } catch (Exception e) {
                                // 单个 chunk 解析失败不终止整条流
                                log.debug("[RAG][ChatModel] stream chunk 解析失败: {}", e.getMessage());
                            }
                        })
                        .onErrorResume(e -> {
                            log.error("[RAG][ChatModel] stream 异常: {}", e.getMessage());
                            return Flux.<ChatResponse>error(
                                    new BizException(500, "chat stream 异常: " + e.getMessage()));
                        });

            } catch (Exception e) {
                log.error("[RAG][ChatModel] stream 建立连接失败: {}", e.getMessage());
                return Flux.<ChatResponse>error(
                        new BizException(500, "chat stream 连接失败: " + e.getMessage()));
            }
        });
    }

    // ============================================================
    // 组装
    // ============================================================

    private HttpRequest buildRequest(URI uri, Message[] messages, boolean stream) {
        try {
            var root = mapper.createObjectNode();
            root.put("model", props.getLlm().getChatModel());
            root.put("temperature", props.getLlm().getTemperature());
            root.put("max_tokens", props.getLlm().getMaxTokens());
            if (stream) {
                root.put("stream", true);
            }

            var arr = root.putArray("messages");
            for (Message m : messages) {
                var node = mapper.createObjectNode();
                if (m instanceof SystemMessage) {
                    node.put("role", "system");
                } else if (m instanceof AssistantMessage) {
                    node.put("role", "assistant");
                } else {
                    node.put("role", "user");
                }
                node.put("content", m.getText());
                arr.add(node);
            }
            String body = mapper.writeValueAsString(root);

            return HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(props.getLlm().getTimeoutSeconds() * (stream ? 3 : 1)))
                    .header("Authorization", "Bearer " + props.getLlm().getApiKey())
                    .header("Content-Type", "application/json")
                    .header("Accept", stream ? "text/event-stream" : "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
        } catch (Exception e) {
            throw new BizException(500, "组装 chat 请求失败: " + e.getMessage());
        }
    }

    private String base() {
        String url = props.getLlm().getBaseUrl();
        return (url != null && url.endsWith("/")) ? url.substring(0, url.length() - 1) : url;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}