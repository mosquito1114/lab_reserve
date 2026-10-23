package com.labreserve.rag.service.impl;

import com.labreserve.common.exception.BizException;
import com.labreserve.rag.config.RagProperties;
import com.labreserve.rag.mapper.RagMessageMapper;
import com.labreserve.rag.mapper.RagSessionMapper;
import com.labreserve.rag.model.entity.RagMessage;
import com.labreserve.rag.model.entity.RagSession;
import com.labreserve.rag.model.vo.SessionVO;
import com.labreserve.rag.service.RagChatHistoryService;
import com.labreserve.util.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link RagChatHistoryService} 实现。
 *
 * <p><b>数据级权限</b>：所有查询都以 {@code user_id = 当前用户} 为前提，
 * 越权访问别人的会话直接 403（而不是返回空）。
 */
@Slf4j
@Service
public class RagChatHistoryServiceImpl implements RagChatHistoryService {

    /** 侧边栏一次拉多少个会话 */
    private static final int RECENT_SESSIONS_LIMIT = 20;

    @Autowired
    private RagSessionMapper sessionMapper;
    @Autowired
    private RagMessageMapper messageMapper;
    @Autowired
    private SecurityUtils securityUtils;
    @Autowired
    private RagProperties props;

    @Override
    public List<SessionVO> listSessions() {
        Long userId = requireUser();
        List<RagSession> sessions = sessionMapper.listRecentByUser(userId, RECENT_SESSIONS_LIMIT);
        List<SessionVO> result = new ArrayList<>(sessions == null ? 0 : sessions.size());
        if (sessions == null) {
            return result;
        }
        for (RagSession s : sessions) {
            SessionVO vo = new SessionVO();
            vo.setId(s.getId());
            vo.setTitle(s.getTitle());
            vo.setMessageCount(s.getMessageCount());
            vo.setLastMessageAt(s.getLastMessageAt());
            vo.setCreatedAt(s.getCreatedAt());
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<RagMessage> listMessages(Long sessionId) {
        Long userId = requireUser();
        requireOwnership(sessionId, userId);

        int limit = Math.max(2, props.getChat().getHistoryWindow() * 2);
        List<RagMessage> messages = messageMapper.listRecent(sessionId, limit);
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        Collections.reverse(messages);
        return messages;
    }

    @Override
    public void deleteSession(Long sessionId) {
        Long userId = requireUser();
        requireOwnership(sessionId, userId);

        // 软删除：status 置 0，会话立即从列表消失、也不再可打开。
        // 消息行保留（审计 + 用量统计），不做物理删除。
        int affected = sessionMapper.softDelete(sessionId, userId);
        if (affected == 0) {
            // 并发场景：已被别处删掉 / 不属于当前用户
            throw BizException.notFound("会话不存在或已删除");
        }
        log.info("[RAG][CHAT] 用户 {} 删除会话 {}（软删除）", userId, sessionId);
    }

    @Override
    public void feedback(Long messageId, Integer feedback) {
        Long userId = requireUser();
        RagMessage msg = messageId == null ? null : messageMapper.selectById(messageId);
        BizException.requireFound(msg, "消息不存在");
        // 数据级权限：只能反馈自己会话里的消息
        RagSession session = sessionMapper.selectById(msg.getSessionId());
        if (session == null || !userId.equals(session.getUserId())) {
            throw BizException.forbidden("无权操作该消息");
        }
        messageMapper.updateFeedback(messageId, feedback);
    }

    // ------------------------------------------------------------

    private Long requireUser() {
        Long userId = securityUtils.getUserId();
        BizException.requireFound(userId, "未登录");
        return userId;
    }

    /**
     * 校验会话存在、未删除、且属于当前用户。
     *
     * <p>已删除的会话按"不存在"处理（404），
     * 避免用户通过手改 id 打开自己删掉的会话。
     */
    private void requireOwnership(Long sessionId, Long userId) {
        RagSession s = sessionId == null ? null : sessionMapper.selectById(sessionId);
        if (s == null || Integer.valueOf(0).equals(s.getStatus())) {
            throw BizException.notFound("会话不存在");
        }
        if (!userId.equals(s.getUserId())) {
            throw BizException.forbidden("无权访问该会话");
        }
    }
}