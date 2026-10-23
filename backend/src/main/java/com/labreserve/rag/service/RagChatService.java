package com.labreserve.rag.service;

import com.labreserve.rag.model.dto.ChatSendDTO;
import com.labreserve.rag.model.vo.RagStreamItem;
import reactor.core.publisher.Flux;

/**
 * RAG 问答服务（QUERY 链）。
 *
 * <p>v3 范围收缩：只做知识库问答，不提供 Tool Call / MCP / 业务接口透传
 * （见 {@code docs/rag-design.md} §十二）。
 */
public interface RagChatService {

    /**
     * 发起一次问答，返回事件流。
     *
     * <p>事件顺序：{@code CHUNK*} → {@code CITATION} → {@code MESSAGE} → {@code DONE}；
     * 降级时在正文前插一条 {@code DEGRADED}。详见 {@link RagStreamItem}。
     */
    Flux<RagStreamItem> chat(ChatSendDTO dto);
}