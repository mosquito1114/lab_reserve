package com.labreserve.rag.controller;

import com.labreserve.common.result.Result;
import com.labreserve.rag.config.RagProperties;
import com.labreserve.rag.model.vo.RagHealthVO;
import com.labreserve.rag.pipeline.chunk.ChunkingStrategy;
import com.labreserve.rag.pipeline.chunk.ChunkingStrategyRegistry;
import io.qdrant.client.QdrantClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * RAG 健康检查接口(R0 阶段)。
 *
 * <p>目的:
 * <ul>
 *   <li>验证配置注入正确(enabled 字段值)</li>
 *   <li>列出注册的切片策略码与名字(让前端可拉)</li>
 *   <li>区分 {@code enabled=true} 时是否真的能把 {@code ChatModel} / {@code QdrantClient}
 *       Bean 装配进来(若不能,说明凭据不全)</li>
 * </ul>
 *
 * <p>R0 阶段不需要鉴权;挂在 {@code /api/rag/health} 在 SecurityConfig 的白名单里,
 * 免登录访问。后续会移除此接口(生产时 RAG 健康监控走 Micrometer + Prometheus,
 * 不开放 HTTP 接口)。
 */
@RestController
@RequestMapping("/api/rag")
@Tag(name = "99-RAG")
public class RagHealthController {

    @Autowired
    private RagProperties ragProperties;

    @Autowired
    private ChunkingStrategyRegistry chunkingRegistry;

    /**
     * 用具体类型 + ObjectProvider 才不会出现 NoUniqueBeanDefinitionException。
     * {@code enabled=false} 时 ChatModel Bean 不会创建,
     * {@code ObjectProvider.getIfAvailable()} 会返 null,表示"还没准备好"。
     */
    @Autowired
    private ObjectProvider<ChatModel> chatModelProvider;

    @Autowired
    private ObjectProvider<QdrantClient> qdrantClientProvider;

    @Operation(summary = "RAG 健康检查", description = "返回 RAG 配置状态、注册的切片策略、依赖 Bean 是否就绪")
    @PostMapping("/health")
    public Result<RagHealthVO> health() {
        boolean enabled = ragProperties.isEnabled();
        boolean chatModelReady = chatModelProvider.getIfAvailable() != null;
        boolean qdrantReady = qdrantClientProvider.getIfAvailable() != null;

        List<ChunkingStrategy> strategies = chunkingRegistry.listAll();

        RagHealthVO vo = new RagHealthVO(
                enabled,
                chatModelReady,
                qdrantReady,
                ragProperties.getIngest().getChunkDefault(),
                strategies.stream().map(s -> new RagHealthVO.StrategyItem(
                        s.code(), s.name(), s.description(),
                        s.recommendedFor(), s.defaultMaxChars(), s.defaultOverlap()
                )).toList());
        return Result.ok(vo);
    }
}
