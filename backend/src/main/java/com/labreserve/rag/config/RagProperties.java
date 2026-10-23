package com.labreserve.rag.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * RAG 模块配置项。
 *
 * <p>对应 application.yml 的 {@code lab-reserve.rag.*} 块。
 * <pre>{@code
 * lab-reserve:
 *   rag:
 *     enabled: false
 *     ingest: { ... }
 *     chat:   { ... }
 * }</pre>
 *
 * <p>Spring Boot 3 推荐的 immutable style：全字段 final + 构造器绑定；本类使用
 * Lombok 简化样板代码。所有内部类不加 {@code @Component}，由 {@code @EnableConfigurationProperties}
 * 在 {@link RagConfig} 上集中启用。
 *
 * @author lab_reserve
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "lab-reserve.rag")
public class RagProperties {

    /** 是否启用 RAG；false 时 {@link SpringAiConfig} 与 {@link QdrantConfig} 不会创建任何外部连接的 Bean */
    private boolean enabled = false;

    /** 摄取链路配置 */
    private Ingest ingest = new Ingest();

    /** 用户问答链路配置 */
    private Chat chat = new Chat();

    /** 问题重写配置 */
    private Rewrite rewrite = new Rewrite();

    /** 重排模型配置（R5 启用） */
    private Rerank rerank = new Rerank();

    /** 提示词文件位置（R5 启用） */
    private Prompt prompt = new Prompt();

    /**
     * LLM 客户端配置 (R2 起, 供 {@code DashScopeEmbeddingClient} 用).
     * 聊天仍走 Spring AI {@code spring.ai.openai.*}; embedding 因为 Spring AI 1.0 GA
     * 与 DashScope OpenAI-兼容模式有 content-type 兼容问题, 走自家 HTTP 客户端.
     */
    private Llm llm = new Llm();

    // ------------------------------------------------------------

    @Getter
    @Setter
    public static class Ingest {
        /** 单文件最大 MB */
        private int maxFileMb = 50;
        /** 全局默认切片策略（RECURSIVE / QA / FIXED_OVERLAP） */
        private String chunkDefault = "RECURSIVE";
        /** 切长上限 */
        private int chunkMaxChars = 500;
        /** 切窗重叠 */
        private int chunkOverlap = 100;
        /** Embedding 批大小 */
        private int embedBatchSize = 16;
        /** 失败重试次数 */
        private int retryAttempts = 3;
        /** 失败重试退避（ms） */
        private long retryBackoffMs = 1000L;
        /** 启动时是否预热 Qdrant Collection */
        private boolean warmupCollection = true;
        /** Qdrant Collection 名 */
        private String collection = "lab_reserve_docs";
        /** 向量维度（与 embedding 模型对齐） */
        private int vectorSize = 1024;
    }

    @Getter
    @Setter
    public static class Chat {
        /** 单用户每分钟最大请求数 */
        private int rateLimitPerUserPerMin = 30;
        /** 全局并发上限（RSemaphore permits） */
        private int globalConcurrency = 16;
        /** 等待队列超时（ms） */
        private long queueWaitTimeoutMs = 30000L;
        /** 单次请求最多携带的 context tokens */
        private int maxContextTokens = 8000;
        /** 留给 LLM 的最近对话轮数 */
        private int historyWindow = 6;
        /** 单次回答 Tool 调用最大循环次数（v3 已取消 Tool Call，保留字段不动） */
        private int toolMaxLoops = 5;
        /**
         * 检索硬拒阈值（rag-design §12.6 / R4 三段式门禁）。
         *
         * <p><b>为何是两个值</b>：小语料下 cosine 区分度不够（实测负样本 0.4638 夹在
         * 正样本 0.4508~0.7121 之间），单一阈值无法分开。改为三段：
         * <ul>
         *   <li>{@code >= kbAcceptScore(0.60)} → 明确相关，直接放行</li>
         *   <li>{@code <  kbRejectScore(0.35)} → 明确不相关，直接拒</li>
         *   <li>中间灰区 → 调 LLM 判证据是否足以回答（见 RelevanceJudge）</li>
         * </ul>
         *
         * <p>{@code kbMinScore} 保留为兼容字段（等同 rejectScore，R3 旧语义）。
         */
        private double kbMinScore = 0.35;

        /** 灰区上界：dense 分 ≥ 此值直接放行（不调 LLM 判相关性） */
        private double kbAcceptScore = 0.60;

        /** 灰区下界：dense 分 < 此值直接拒 */
        private double kbRejectScore = 0.35;
    }

    @Getter
    @Setter
    public static class Rewrite {
        private boolean enabled = true;
        private int timeoutSec = 15;
    }

    @Getter
    @Setter
    public static class Rerank {
        private boolean enabled = false;
        private int topKIn = 12;
        private int topKOut = 5;
        // 仅占位，实际 Reranker 模型在 R5 引入
        private List<String> candidateModels = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class Prompt {
        private String system;
        private String rewrite;
        private String intent;
        private String answer;
    }

    @Getter
    @Setter
    public static class Llm {
        /** 供应商标识（注释用, 不参与代码逻辑） */
        private String provider = "tongyi";
        /** OpenAI-兼容 API 基础 URL */
        private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        /** API key */
        private String apiKey = "";
        /** 聊天模型名（R3 ChatModel 用 spring.ai.openai.chat.options.model, 不走这里） */
        private String chatModel = "qwen-plus";
        /** Embedding 模型名 */
        private String embeddingModel = "text-embedding-v3";
        /** HTTP 超时（秒） */
        private int timeoutSeconds = 30;
        /** 采样温度：低温度 → 引用类回答更稳定 */
        private double temperature = 0.2;
        /** 单次回答最大 token 数 */
        private int maxTokens = 2000;
    }
}
