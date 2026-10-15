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
        /** 单次回答 Tool 调用最大循环次数 */
        private int toolMaxLoops = 5;
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
}
