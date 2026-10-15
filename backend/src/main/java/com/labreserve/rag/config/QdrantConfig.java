package com.labreserve.rag.config;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Qdrant 客户端 Bean 配置。
 *
 * <p><b>只在 RAG 启用时创建</b>（{@code lab-reserve.rag.enabled=true}），
 * 启用时也不真正建立 gRPC 连接 —— {@link QdrantClient} 是轻量句柄,
 * 真正连是第一次 RPC 时。R0 阶段只验证 Bean 能否被装配。
 *
 * <p>Qdrant 没有官方 Spring Boot starter,连接参数从环境变量读取
 * （{@code QDRANT_HOST} / {@code QDRANT_GRPC_PORT} / {@code QDRANT_API_KEY}）,
 * 不引新依赖。R1 联调时再补 TLS / API key 注入细节。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "lab-reserve.rag.enabled", havingValue = "true")
public class QdrantConfig {

    @Bean(destroyMethod = "close")
    public QdrantClient qdrantClient(org.springframework.core.env.Environment env) {
        String host = env.getProperty("QDRANT_HOST", "localhost");
        int port = Integer.parseInt(env.getProperty("QDRANT_GRPC_PORT", "6334"));

        QdrantClient client = new QdrantClient(
                QdrantGrpcClient.newBuilder(host, port, /* useTls= */ false).build());
        log.info("[RAG] 初始化 QdrantClient: host={} port={} (gRPC, ssl=false)", host, port);
        return client;
    }
}
