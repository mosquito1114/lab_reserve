package com.labreserve.rag.config;

import com.labreserve.rag.util.MdcTaskDecorator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * RAG 模块的独立线程池。
 *
 * <p>两个职责：
 * <ul>
 *   <li>{@code ragIngestExecutor}：摄取链路（{@code @Async("ragIngestExecutor")}），
 *       文档上传 / 重建 / Step 重试都走这里；与日志线程池隔离，避免被日志写入阻塞。</li>
 *   <li>全站 {@link EnableAsync} 在此启用（不在 {@code LabReserveApplication} 上加），
 *       避免影响现有业务默认线程池行为。</li>
 * </ul>
 *
 * <p>复用现有 {@code @Async} 机制，不引新中间件；详见 {@code docs/rag-design.md §三}。
 */
@Slf4j
@Configuration
@EnableAsync
@EnableConfigurationProperties(RagProperties.class)
public class RagAsyncConfig {

    /** 摄取管道线程池 Bean 名（@Async 调用方按此引用） */
    public static final String INGEST_EXECUTOR = "ragIngestExecutor";

    @Autowired
    private MdcTaskDecorator mdcTaskDecorator;

    @Bean(name = INGEST_EXECUTOR)
    public ThreadPoolTaskExecutor ragIngestExecutor(RagProperties props) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 摄取链路允许并发跑多个文档，但每个文档的 Step 串行
        executor.setCorePoolSize(Math.max(2, props.getIngest().getEmbedBatchSize() / 4));
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("rag-ingest-");
        // 队列满 → 主线程跑，避免摄取任务静默丢失
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        // MDC 透传（design.md §11.1 第 5 项）：traceId 跨 @Async 线程串起来
        executor.setTaskDecorator(mdcTaskDecorator);
        executor.initialize();
        log.info("[RAG] 初始化摄取线程池（with MDC propagation）: core={} max={} queue={}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        return executor;
    }
}
