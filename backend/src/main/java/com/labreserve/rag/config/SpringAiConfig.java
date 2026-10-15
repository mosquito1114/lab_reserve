package com.labreserve.rag.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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
 * <p>{@link ChatModel} / {@code EmbeddingModel} 由 {@code spring-ai-starter-model-openai}
 * 自动装配提供；这里只在其上构造 {@link ChatClient}：
 * <ul>
 *   <li>{@code primaryLlm}：默认 chat client（意图识别 / 合成 / 工具调用 等主路径）</li>
 *   <li>{@code cheapLlm}：便宜快速模型占位（用于问题重写；R3 启用后再分离）</li>
 * </ul>
 *
 * <p>之所以 @ConditionalOnBean(ChatModel.class) 是为了应对：
 * <ul>
 *   <li>{@code spring.ai.openai.api-key} 为空时，自动装配会跳过 ChatModel 创建；</li>
 *   <li>此时连 wrapper 都不该创建，否则 {@code primaryLlm} 注入会失败。</li>
 * </ul>
 *
 * @see RagProperties
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "lab-reserve.rag.enabled", havingValue = "true")
@ConditionalOnBean(ChatModel.class)
public class SpringAiConfig {

    /** 主 ChatClient —— 用于意图识别 / 合成 / Tool 调用 等大部分 Step */
    @Bean
    @Primary
    public ChatClient primaryLlm(ChatModel chatModel) {
        log.info("[RAG] 初始化主 ChatClient");
        return ChatClient.builder(chatModel).build();
    }

    /** 便宜模型 ChatClient；R0 阶段复用 ChatModel，R3 起接更便宜模型（如 deepseek-chat 重写用） */
    @Bean
    public ChatClient cheapLlm(ChatModel chatModel) {
        log.info("[RAG] 初始化便宜 ChatClient（占位，与 primary 同一 ChatModel；R3 改造）");
        return ChatClient.builder(chatModel).build();
    }
}
