package com.labreserve.rag.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 单步重跑入参（{@code POST /api/rag/admin/documents/{id}/steps/{code}/run}）。
 *
 * <p>当前 R1 仅承载透传文本（备用）；后续 R2 再加 step 级 JSON 参数约定。
 *
 * <p>鉴权由 Controller 上的 {@code @RequirePerm} 限定 {@code rag:doc:reindex}。
 */
@Data
@Schema(description = "单步重跑入参（当前 R1 仅透传 text；扩展性预留）")
public class StepRunDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 透传备注（限长），会写进 {@code tb_rag_pipeline_step.error_msg}（成功时无意义）。
     */
    @Schema(description = "可选备注：管理员手动触发原因的说明，会写入步骤日志", example = "修复切片策略后手动重跑 CHUNK")
    @Size(max = 2000, message = "备注长度不能超过 2000")
    private String remark;
}
