package com.labreserve.rag.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 单步重跑入参（{@code POST /api/rag/admin/documents/run_step}）。
 *
 * <p>把"路径变量 + step 入参"合并到一个 DTO——本项目接口统一 {@code POST + JSON body}，
 * 见 AGENTS.md §3。业务入参（{@link #remark}）透传到日志。
 */
@Data
@Schema(description = "单步重跑入参（admin 手动跑某 Step）")
public class StepRunPathDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "目标文档 ID", example = "1")
    @NotNull(message = "文档 ID 不能为空")
    private Long documentId;

    @Schema(description = "Step code，R1 支持 EXTRACT/SANITIZE/CHUNK", example = "CHUNK",
            allowableValues = {"EXTRACT", "SANITIZE", "CHUNK"})
    @NotBlank(message = "Step code 不能为空")
    @Size(max = 32)
    private String stepCode;

    @Schema(description = "备注，写入操作日志", example = "修复切片策略后手动重跑 CHUNK")
    @Size(max = 500)
    private String remark;
}
