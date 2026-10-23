package com.labreserve.rag.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * RAG 摄取步骤日志（{@code tb_rag_pipeline_step}）。
 *
 * <p>每跑一步落一行;{@code status}:
 * <ul>
 *   <li>1 RUNNING</li>
 *   <li>2 SUCCESS</li>
 *   <li>3 FAILED</li>
 *   <li>4 SKIPPED(R2 才用)</li>
 * </ul>
 *
 * <p>{@code output_meta} 是 JSON 字符串(由 Jackson 序列化 Map 得到)。
 */
@Data
@TableName("tb_rag_pipeline_step")
public class RagPipelineStep implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long documentId;
    private Integer ingestVersion;

    /** EXTRACT / SANITIZE / CHUNK / EMBED / UPSERT / FINALIZE */
    private String stepCode;

    /** 1 RUNNING / 2 SUCCESS / 3 FAILED / 4 SKIPPED */
    private Integer status;

    private String inputSha256;
    private String outputSha256;
    private String outputMeta;
    private String traceId;
    private Integer costMs;
    private String errorMsg;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long parentStepId;

    /** 数据库 DEFAULT CURRENT_TIMESTAMP 维护 */
    private LocalDateTime createdAt;
}
