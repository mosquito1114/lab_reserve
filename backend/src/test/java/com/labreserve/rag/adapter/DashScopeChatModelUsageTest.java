package com.labreserve.rag.adapter;

import com.labreserve.rag.config.RagProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;

import com.sun.net.httpserver.HttpServer;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DashScopeChatModel} 的 usage 解析测试。
 *
 * <p><b>为什么要离线测</b>：真实 DashScope 需要额度，且 usage 字段名有两种方言
 * （OpenAI 兼容 vs 原生），用本地假 HTTP 服务可以稳定覆盖两条路径。
 *
 * <p>覆盖：
 * <ul>
 *   <li>非流式：{@code usage} → {@code ChatResponse.metadata}</li>
 *   <li>流式：usage-only chunk（正文空、只有 usage）能被解析出来</li>
 *   <li>请求确实带上了 {@code stream_options.include_usage = true}</li>
 *   <li>两种字段方言都能识别</li>
 * </ul>
 */
class DashScopeChatModelUsageTest {

    private HttpServer server;
    private DashScopeChatModel model;
    /** 收到的请求体（用于断言 stream_options） */
    private final List<String> receivedBodies = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();

        RagProperties props = new RagProperties();
        props.getLlm().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
        props.getLlm().setApiKey("test-key");
        props.getLlm().setChatModel("qwen-plus");
        props.getLlm().setTimeoutSeconds(10);

        model = new DashScopeChatModel();
        ReflectionTestUtils.setField(model, "props", props);
        ReflectionTestUtils.invokeMethod(model, "init");
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    // ============================================================
    // 非流式
    // ============================================================

    @Test
    @DisplayName("非流式：OpenAI 方言 usage（prompt_tokens/completion_tokens/total_tokens）")
    void shouldParseOpenAiStyleUsage() throws Exception {
        server.createContext("/v1/chat/completions", exchange -> {
            readBody(exchange);
            String body = """
                    {"id":"1","object":"chat.completion","model":"qwen-plus",
                     "choices":[{"index":0,"message":{"role":"assistant","content":"你好"},"finish_reason":"stop"}],
                     "usage":{"prompt_tokens":120,"completion_tokens":45,"total_tokens":165}}
                    """;
            respond(exchange, body);
        });

        ChatResponse resp = model.call(prompt());
        assertEquals("你好", resp.getResult().getOutput().getText());
        assertNotNull(resp.getMetadata().getUsage());
        assertEquals(120, resp.getMetadata().getUsage().getPromptTokens());
        assertEquals(45, resp.getMetadata().getUsage().getCompletionTokens());
        assertEquals(165, resp.getMetadata().getUsage().getTotalTokens());
    }

    @Test
    @DisplayName("非流式：DashScope 原生方言 usage（input_tokens/output_tokens）")
    void shouldParseDashScopeNativeUsage() throws Exception {
        server.createContext("/v1/chat/completions", exchange -> {
            readBody(exchange);
            String body = """
                    {"id":"1","object":"chat.completion",
                     "choices":[{"index":0,"message":{"role":"assistant","content":"ok"}}],
                     "usage":{"input_tokens":88,"output_tokens":22,"total_tokens":110}}
                    """;
            respond(exchange, body);
        });

        ChatResponse resp = model.call(prompt());
        assertEquals(88, resp.getMetadata().getUsage().getPromptTokens());
        assertEquals(22, resp.getMetadata().getUsage().getCompletionTokens());
        assertEquals(110, resp.getMetadata().getUsage().getTotalTokens());
    }

    @Test
    @DisplayName("非流式：没有 usage 字段 → metadata 的 usage 为 null（上层降级为本地估算）")
    void shouldTolerateMissingUsage() throws Exception {
        server.createContext("/v1/chat/completions", exchange -> {
            readBody(exchange);
            respond(exchange, """
                    {"id":"1","choices":[{"index":0,"message":{"role":"assistant","content":"ok"}}]}
                    """);
        });

        ChatResponse resp = model.call(prompt());
        assertEquals("ok", resp.getResult().getOutput().getText());
        // Spring AI 在没有 usage 时返回 EmptyUsage 对象（不是 null），
        // 三个 token 字段都是 null → LlmUsage.from 必须安全地转成 EMPTY
        assertEquals(com.labreserve.rag.model.vo.LlmUsage.EMPTY,
                com.labreserve.rag.model.vo.LlmUsage.from(resp));
    }

    // ============================================================
    // 流式
    // ============================================================

    @Test
    @DisplayName("流式：usage-only chunk 能被解析（正文空、只有 usage）")
    void shouldParseUsageOnlyChunkInStream() throws Exception {
        server.createContext("/v1/chat/completions", exchange -> {
            readBody(exchange);
            String sse = """
                    data: {"choices":[{"delta":{"content":"你"}}]}

                    data: {"choices":[{"delta":{"content":"好"}}]}

                    data: {"choices":[],"usage":{"prompt_tokens":200,"completion_tokens":60,"total_tokens":260}}

                    data: [DONE]

                    """;
            respondSse(exchange, sse);
        });

        List<ChatResponse> responses = new ArrayList<>();
        model.stream(prompt()).doOnNext(responses::add).blockLast(java.time.Duration.ofSeconds(10));

        assertEquals(3, responses.size(), "应有 2 个正文 chunk + 1 个 usage chunk");
        assertEquals("你", responses.get(0).getResult().getOutput().getText());
        assertEquals("好", responses.get(1).getResult().getOutput().getText());

        ChatResponse usageResp = responses.get(2);
        assertNotNull(usageResp.getMetadata().getUsage());
        assertEquals(200, usageResp.getMetadata().getUsage().getPromptTokens());
        assertEquals(60, usageResp.getMetadata().getUsage().getCompletionTokens());
        assertEquals(260, usageResp.getMetadata().getUsage().getTotalTokens());
    }

    @Test
    @DisplayName("流式：请求必须带 stream_options.include_usage=true")
    void shouldRequestUsageInStreamMode() throws Exception {
        server.createContext("/v1/chat/completions", exchange -> {
            readBody(exchange);
            respondSse(exchange, "data: [DONE]\n\n");
        });

        model.stream(prompt()).blockLast(java.time.Duration.ofSeconds(10));

        assertEquals(1, receivedBodies.size());
        String body = receivedBodies.get(0);
        assertTrue(body.contains("\"stream\":true"), "应是流式请求: " + body);
        assertTrue(body.contains("\"include_usage\":true"), "应带 stream_options.include_usage: " + body);
    }

    @Test
    @DisplayName("流式：provider 不返回 usage 时不产生 usage chunk（上层走本地估算）")
    void shouldNotEmitUsageChunkWhenAbsent() throws Exception {
        server.createContext("/v1/chat/completions", exchange -> {
            readBody(exchange);
            respondSse(exchange, """
                    data: {"choices":[{"delta":{"content":"你好"}}]}

                    data: [DONE]

                    """);
        });

        List<ChatResponse> responses = new ArrayList<>();
        model.stream(prompt()).doOnNext(responses::add).blockLast(java.time.Duration.ofSeconds(10));

        assertEquals(1, responses.size());
        assertEquals("你好", responses.get(0).getResult().getOutput().getText());
    }

    // ============================================================
    // 辅助
    // ============================================================

    private static Prompt prompt() {
        return new Prompt(List.of(new SystemMessage("你是助手"), new UserMessage("你好")));
    }

    private void readBody(com.sun.net.httpserver.HttpExchange exchange) throws java.io.IOException {
        byte[] buf = exchange.getRequestBody().readAllBytes();
        receivedBodies.add(new String(buf, StandardCharsets.UTF_8));
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void respondSse(com.sun.net.httpserver.HttpExchange exchange, String sse) throws java.io.IOException {
        byte[] bytes = sse.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
