package com.labreserve.rag.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * RAG 会话消息（{@code tb_rag_message}）。
 *
 * <p>R3 起启用。一问一答落两行：{@code USER}（用户问）+ {@code ASSISTANT}（模型答）。
 *
 * <h3>v3 范围收缩说明（rag-design §十二）</h3>
 * <ul>
 *   <li>{@code role} 只写入 {@code USER} / {@code ASSISTANT}（以及 R5 摘要压缩后的 {@code SYSTEM}）。
 *       {@code TOOL} 枚举值保留在 {@code RagRole} 但永不产生。</li>
 *   <li>{@code toolCalls} 列<b>保留为 reserved，永远写 NULL</b>（不做 V7 drop）。</li>
 *   <li>{@code rewritten} 存问题重写结果（多轮指代补全后的独立可检索问句）。</li>
 *   <li>{@code intent} 存意图分类结果 JSON（docs/chitchat/unsafe/out_of_scope）。</li>
 *   <li>{@code citations} 存引用列表 JSON（引用到的 chunk / document）。</li>
 * </ul>
 *
 * @see RagSession
 */
@Data
@TableName("tb_rag_message")
public class RagMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    /** USER / ASSISTANT / SYSTEM（v3 起不产生 TOOL） */
    private String role;

    private String content;

    /** 问题重写结果（USER 角色补充字段）；chat_content 过长时截断到 2000 字 */
    private String rewritten;

    /** 意图分类结果 JSON（USER 角色补充字段） */
    private String intent;

    /** 引用列表 JSON：[{documentId, chunkId, title, snippet, score}]（ASSISTANT 角色） */
    private String citations;

    /** v3 reserved：永远 NULL（见类注释） */
    private String toolCalls;

    private Integer inputTokens;
    private Integer outputTokens;
    private Integer costMs;

    /** 1 有用 / 0 没用（用户反馈，R3 落地） */
    private Integer feedback;

    /** traceId：贯穿 Gate → Rewrite → Intent → Retrieve → Synthesize → Persist */
    private String traceId;

    /** 数据库 DEFAULT CURRENT_TIMESTAMP 维护 */
    private LocalDateTime createdAt;
}