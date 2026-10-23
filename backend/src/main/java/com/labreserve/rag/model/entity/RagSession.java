package com.labreserve.rag.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * RAG 会话（{@code tb_rag_session}）。
 *
 * <p>R3 起启用。多轮对话的载体：
 * <ul>
 *   <li>{@code summary} + {@code summaryUpTo} —— 历史摘要与它覆盖到的 messageId（R5 摘要压缩用）</li>
 *   <li>{@code lastTokenWindow} —— 最近 N 轮累计 token，超阈值触发压缩（R3 只统计不压缩）</li>
 *   <li>{@code lastMessageAt} —— 列表排序用（{@code idx_user_lastmsg} 索引）</li>
 * </ul>
 *
 * @see RagMessage
 */
@Data
@TableName("tb_rag_session")
public class RagSession implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 会话标题，默认「新会话」，首条 USER 消息截断后回填 */
    private String title;

    private Integer messageCount;

    /** 历史摘要（SYSTEM 角色压缩结果）；R3 只读不写 */
    private String summary;

    /** 摘要覆盖到的最大 message_id；R3 只读不写 */
    private Long summaryUpTo;

    /** 最近 N 轮累计 token，超 {@code chat.max-context-tokens} 触发压缩（R3 只统计） */
    private Integer lastTokenWindow;

    private LocalDateTime lastMessageAt;

    /** 数据库 DEFAULT CURRENT_TIMESTAMP 维护 */
    private LocalDateTime createdAt;

    /** 数据库 ON UPDATE CURRENT_TIMESTAMP 维护 */
    private LocalDateTime updatedAt;
}