package com.labreserve.rag.controller;

import com.labreserve.common.annotation.OpLog;
import com.labreserve.common.constant.PermissionConst;
import com.labreserve.common.enums.OperationModule;
import com.labreserve.common.enums.OperationType;
import com.labreserve.common.exception.BizException;
import com.labreserve.common.result.PageResult;
import com.labreserve.common.result.Result;
import com.labreserve.model.dto.IdDTO;
import com.labreserve.rag.mapper.RagMessageMapper;
import com.labreserve.rag.mapper.RagSessionMapper;
import com.labreserve.rag.model.dto.ChatSendDTO;
import com.labreserve.rag.model.dto.FeedbackDTO;
import com.labreserve.rag.model.entity.RagMessage;
import com.labreserve.rag.model.entity.RagSession;
import com.labreserve.rag.model.vo.RagStreamItem;
import com.labreserve.rag.model.vo.SessionVO;
import com.labreserve.rag.service.RagChatService;
import com.labreserve.rag.service.RagChatHistoryService;
import com.labreserve.security.RequirePerm;
import com.labreserve.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;

/**
 * RAG 用户侧问答接口（{@code /api/rag/chat/**}）。
 *
 * <p>v3 范围收缩：只做知识库问答，无 Tool Call / MCP / 业务接口透传
 * （见 {@code docs/rag-design.md} §十二）。
 *
 * <h3>流式协议</h3>
 * {@code POST /send} 返回 {@code text/event-stream}，事件顺序：
 * <pre>
 *   chunk*     正文增量（前端追加渲染）
 *   citation   引用列表（可追溯到 chunkId / vectorRef）
 *   message    最终 messageId + token 统计 + 耗时（可提交反馈）
 *   degraded   走了降级路径（可选，出现在正文之前）
 *   done       正常结束
 *   error      异常结束
 * </pre>
 *
 * <p>操作日志：{@code /send} <b>不</b>打 {@code @OpLog}——
 * 提问内容属用户隐私且量大，走 {@code tb_rag_message} 留存即可；
 * 只有 {@code /feedback} 写操作才打日志。
 */
@Slf4j
@RestController
@RequestMapping("/api/rag/chat")
@Tag(name = "97-RAG-问答")
public class RagChatController {

    @Autowired
    private RagChatService ragChatService;

    @Autowired
    private RagChatHistoryService historyService;

    @Autowired
    private ObjectMapper objectMapper;

    // ============================================================
    // 提问（SSE 流式）
    // ============================================================

    @Operation(summary = "发起问答（SSE 流式）",
            description = "返回 text/event-stream；事件 chunk/citation/message/degraded/done/error。"
                    + "仅基于知识库作答，未命中会硬拒不调 LLM。")
    @RequirePerm(PermissionConst.RAG_CHAT_QUERY)
    @PostMapping(value = "/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> send(@RequestBody @Valid ChatSendDTO dto) {
        // 限频与全局并发的检查放在 service 内的 Flux.defer 里，
        // 这里只做一次轻量前置校验（未登录直接 401，不进 SSE）
        if (securityUtilsNotLogged()) {
            throw new BizException(com.labreserve.common.result.ResultCode.UNAUTHORIZED, "未登录或登录已失效，请重新登录");
        }

        StreamingResponseBody body = outputStream -> {
            try {
                // toIterable() 阻塞式消费：在 Spring 分配的异步线程上逐事件写入
                for (RagStreamItem item : ragChatService.chat(dto).toIterable()) {
                    outputStream.write(frame(item));
                    outputStream.flush();
                }
            } catch (Exception e) {
                // 客户端断开（IOException）或业务异常：尽最大努力补一条 error 事件
                log.warn("[RAG][SSE] 流中断: {}", rootMessage(e));
                try {
                    outputStream.write(frame(new RagStreamItem(
                            RagStreamItem.Kind.ERROR, null, null, null,
                            null, null, null, rootMessage(e))));
                    outputStream.flush();
                } catch (Exception ignored) {
                    // 连接已断，忽略
                }
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header("Cache-Control", "no-cache")
                // 关掉 nginx 的响应缓冲，否则流式会被攒成一坨
                .header("X-Accel-Buffering", "no")
                .body(body);
    }

    /**
     * 把一个事件序列化成 SSE 帧：{@code event:{kind}
data:{json}

}。
     */
    private byte[] frame(RagStreamItem item) throws Exception {
        String json;
        try {
            json = objectMapper.writeValueAsString(item);
        } catch (Exception e) {
            json = "{\"kind\":\"ERROR\",\"error\":\"序列化失败\"}";
        }
        String text = "event:" + item.getKind().name().toLowerCase()
                + "\ndata:" + json + "\n\n";
        return text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        return msg == null ? cur.getClass().getSimpleName() : msg;
    }

    // ============================================================
    // 会话历史
    // ============================================================

    @Operation(summary = "我的会话列表", description = "按最近活跃时间倒序")
    @RequirePerm(PermissionConst.RAG_CHAT_HISTORY)
    @PostMapping("/sessions")
    public Result<List<SessionVO>> sessions() {
        return Result.ok(historyService.listSessions());
    }

    @Operation(summary = "会话消息历史", description = "按时间正序返回该会话的消息")
    @RequirePerm(PermissionConst.RAG_CHAT_HISTORY)
    @PostMapping("/messages")
    public Result<List<RagMessage>> messages(@RequestBody @Valid IdDTO dto) {
        return Result.ok(historyService.listMessages(dto.getId()));
    }

    @Operation(summary = "删除会话", description = "逻辑删除：仅本人可删；消息保留用于审计")
    @RequirePerm(PermissionConst.RAG_CHAT_HISTORY)
    @OpLog(module = OperationModule.RAG, type = OperationType.DELETE, content = "删除 RAG 会话")
    @PostMapping("/delete_session")
    public Result<Void> deleteSession(@RequestBody @Valid IdDTO dto) {
        historyService.deleteSession(dto.getId());
        return Result.ok();
    }

    // ============================================================
    // 反馈
    // ============================================================

    @Operation(summary = "消息反馈", description = "1 有用 / 0 没用；用于评估门禁的数据积累")
    @RequirePerm(PermissionConst.RAG_CHAT_HISTORY)
    @OpLog(module = OperationModule.RAG, type = OperationType.UPDATE, content = "RAG 消息反馈")
    @PostMapping("/feedback")
    public Result<Void> feedback(@RequestBody @Valid FeedbackDTO dto) {
        historyService.feedback(dto.getMessageId(), dto.getFeedback());
        return Result.ok();
    }

    // ------------------------------------------------------------

    @Autowired
    private SecurityUtils securityUtils;

    private boolean securityUtilsNotLogged() {
        return securityUtils.getUserId() == null;
    }
}