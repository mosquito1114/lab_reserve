package com.labreserve.rag.model.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文档详情 VO（{@code GET /api/rag/admin/documents/{id}} 的返回体）。
 *
 * <p>对照 {@link com.labreserve.rag.model.entity.RagDocument}，做字段平坦化（无脱敏 VO，
 * 管理员身份才能调此接口）；错误信息原样输出（已经是 200 路径上消费）。
 *
 * <p>{@code pipelineState} 是枚举 {@link com.labreserve.rag.enums.RagPipelineState#name()}
 * 的字符串值（见 V2 迁移脚本）；{@code status} 是业务维度启用停用标志，与 pipeline_state 解耦。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "文档详情")
public class RagDocumentVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "类别 code（REGULATION/MANUAL/FAQ/SOP）")
    private String category;

    @Schema(description = "来源类型 PDF/DOCX/MD/TXT/PPT/XLSX")
    private String sourceType;

    @Schema(description = "OSS URL")
    private String fileUrl;

    @Schema(description = "清洗后正文字符数")
    private Integer charCount;

    @Schema(description = "切片数")
    private Integer chunkCount;

    @Schema(description = "状态机当前态（CREATED/EXTRACTING/.../READY/DISABLED/FAILED）")
    private String pipelineState;

    @Schema(description = "摄取版本号（每次 reingest +1）")
    private Integer ingestVersion;

    @Schema(description = "内容 sha256（幂等保护键）")
    private String contentSha256;

    @Schema(description = "最近失败步骤 code")
    private String lastErrorStep;

    @Schema(description = "最近失败消息（截断 2000 字）")
    private String lastErrorMsg;

    @Schema(description = "最近失败时间")
    private LocalDateTime lastErrorAt;

    @Schema(description = "切片策略 code（NULL=走全局默认）")
    private String chunkingStrategy;

    @Schema(description = "文档级切长上限（NULL=走策略/全局默认）")
    private Integer chunkMaxChars;

    @Schema(description = "文档级重叠（NULL=走策略/全局默认）")
    private Integer chunkOverlap;

    @Schema(description = "业务启用停用（1 启用 / 0 停用，与 pipeline_state 解耦）")
    private Integer status;

    @Schema(description = "上传人 user_id")
    private Long uploadedBy;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
