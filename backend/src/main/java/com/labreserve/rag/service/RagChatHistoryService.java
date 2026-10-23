package com.labreserve.rag.service;

import com.labreserve.rag.model.entity.RagMessage;
import com.labreserve.rag.model.vo.SessionVO;

import java.util.List;

/**
 * RAG 会话历史服务（用户侧）。
 *
 * <p>只操作<b>当前用户自己的</b>会话与消息（数据级权限在 service 层强制）。
 */
public interface RagChatHistoryService {

    /** 我的会话列表（最近活跃倒序） */
    List<SessionVO> listSessions();

    /**
     * 会话消息历史（时间正序）。
     *
     * @param sessionId 会话 ID（必须属于当前用户，否则 403）
     */
    List<RagMessage> listMessages(Long sessionId);

    /**
     * 删除会话（逻辑删除，仅本人）。
     *
     * @param sessionId 会话 ID
     */
    void deleteSession(Long sessionId);

    /**
     * 提交消息反馈。
     *
     * @param messageId 消息 ID
     * @param feedback  1 有用 / 0 没用
     */
    void feedback(Long messageId, Integer feedback);
}