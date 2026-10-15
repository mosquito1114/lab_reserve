package com.labreserve.rag.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 模块的总开关。
 *
 * <p>启用 {@link RagProperties}（绑定 {@code lab-reserve.rag.*} 配置），
 * 并且在 {@code lab-reserve.rag.enabled=false} 时让其他 RAG 配置类
 * （{@link SpringAiConfig} / {@link QdrantConfig} 等）用
 * {@code @ConditionalOnProperty} 跳过 Bean 创建，保证零外部依赖即可启动。
 */
@Configuration
@EnableConfigurationProperties(RagProperties.class)
public class RagConfig {
}
