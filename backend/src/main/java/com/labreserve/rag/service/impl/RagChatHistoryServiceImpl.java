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

        // 逻辑删除：消息保留用于审计（需求：日志只增不改不删）
        RagSession s = sessionMapper.selectById(sessionId);
        if (s != null) {
            s.setTitle("[已删除] " + (s.getTitle() == null ? "" : s.getTitle()));
            s.setLastMessageAt(LocalDateTime.now());
            sessionMapper.updateById(s);
        }
        log.info("[RAG][CHAT] 用户 {} 删除会话 {}", userId, sessionId);
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

    /** 校验会话属于当前用户 */
    private void requireOwnership(Long sessionId, Long userId) {
        RagSession s = sessionId == null ? null : sessionMapper.selectById(sessionId);
        BizException.requireFound(s, "会话不存在");
        if (!userId.equals(s.getUserId())) {
            throw BizException.forbidden("无权访问该会话");
        }
    }
}