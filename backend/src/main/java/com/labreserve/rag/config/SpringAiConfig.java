package com.labreserve.rag.config;

import com.labreserve.common.exception.BizException;
import com.labreserve.rag.adapter.DashScopeChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Spring AI Client Bean 注册。
 *
 * <p><b>只在 RAG 启用时创建</b>（{@code lab-reserve.rag.enabled=true}）；
 * 未启用时这些 Bean 都不会被实例化，启动期不连任何外部服务。
 *
 * <h3>ChatModel 的来源优先级（R3 起）</h3>
 * <ol>
 *   <li><b>{@link DashScopeChatModel}</b> —— 自家 HTTP 客户端，<b>无条件生效</b>
 *       （只要 {@code RAG_ENABLED=true} + {@code LLM_API_KEY} 非空）。
 *       绕开 Spring AI 自动装配与 {@code spring.ai.model.chat=none} 的耦合。</li>
 *   <li>{@link ChatModel} 自动装配 —— 仅当 {@code SPRING_AI_MODEL_CHAT=openai} 时存在；
 *       作为 {@link DashScopeChatModel} 缺失时的兼容回退。</li>
 * </ol>
 *
 * <p>之所以这样设计：dev 分支 {@code 4a63c80} 把 {@code spring.ai.model.chat} 默认关成
 * {@code none}，导致自动装配不建 ChatModel。RAG 不能依赖"协作者记得切环境变量"，
 * 所以走 (1) 自建，(2) 只当保险。
 *
 * @see DashScopeChatModel
 * @see com.labreserve.rag.adapter.DashScopeEmbeddingClient
 * @see RagProperties
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "lab-reserve.rag.enabled", havingValue = "true")
public class SpringAiConfig {

    /**
     * 主 ChatClient —— 用于意图识别 / 合成 / 重写 等主路径。
     * 优先用 {@link DashScopeChatModel}，没有则回退到 Spring AI 自动装配的 {@link ChatModel}。
     */
    @Bean
    @Primary
    public ChatClient primaryLlm(ObjectProvider<ChatModel> chatModelProvider,
                                  DashScopeChatModel dashScopeChatModel) {
        ChatModel model = pickChatModel(chatModelProvider, dashScopeChatModel);
        log.info("[RAG] 初始化主 ChatClient（model={}）", model.getClass().getSimpleName());
        return ChatClient.builder(model).build();
    }

    /**
     * 便宜模型 ChatClient；R0 阶段复用同一 ChatModel。
     * R3 起可接更便宜的模型单独做 QueryRewriter（当前不做，Qwen 没有明显更便宜的档位）。
     */
    @Bean
    public ChatClient cheapLlm(ObjectProvider<ChatModel> chatModelProvider,
                               DashScopeChatModel dashScopeChatModel) {
        ChatModel model = pickChatModel(chatModelProvider, dashScopeChatModel);
        log.info("[RAG] 初始化便宜 ChatClient（与 primary 同一 model）");
        return ChatClient.builder(model).build();
    }

    /** DashScope 优先，其次 Spring AI 自动装配，都没有则报错 */
    private ChatModel pickChatModel(ObjectProvider<ChatModel> provider, DashScopeChatModel dashScope) {
        if (dashScope != null) {
            return dashScope;
        }
        ChatModel auto = provider.getIfAvailable();
        if (auto == null) {
            throw new BizException(503, "RAG ChatModel 未就绪：DashScopeChatModel 与 Spring AI 自动装配均不可用");
        }
        return auto;
    }
}
