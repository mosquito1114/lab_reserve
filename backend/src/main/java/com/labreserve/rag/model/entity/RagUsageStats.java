package com.labreserve.rag.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * RAG 使用统计（{@code tb_rag_usage_stats}）。
 *
 * <p>R3 起写入。每条问答一行，供运营报表与成本核算（docs §3.9）。
 *
 * <h3>v3 说明</h3>
 * {@code toolCalls} 保留为 reserved，永远写 0。
 */
@Data
@TableName("tb_rag_usage_stats")
public class RagUsageStats implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 角色编码（STUDENT / TEACHER / ...），便于按角色看用量 */
    private String roleCode;

    /** 意图分类结果（docs / chitchat / unsafe / out_of_scope） */
    private String intent;

    /** 本次消耗 token（粗估：输入 + 输出） */
    private Integer usedTokens;

    /** 命中的文档/切片数（docs 意图才有值，否则 0） */
    private Integer docHits;

    /** v3 reserved：永远 0（Tool Calling 已取消） */
    private Integer toolCalls;

    /** 本次问答端到端耗时（ms） */
    private Integer costMs;

    /** 是否走了降级路径（1 走了 / 0 没有） */
    private Integer degraded;

    /** 数据库 DEFAULT CURRENT_TIMESTAMP 维护 */
    private LocalDateTime createdAt;
}