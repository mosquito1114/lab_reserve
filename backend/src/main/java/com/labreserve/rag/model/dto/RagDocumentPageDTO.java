package com.labreserve.rag.model.dto;

import com.labreserve.common.result.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 知识库文档分页查询（{@code POST /api/rag/admin/list}）。
 *
 * <p>对应前端「知识库管理」页的筛选栏：标题关键词 / 类别 / 流水线状态 / 业务启停。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "知识库文档分页查询")
public class RagDocumentPageDTO extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "标题关键词（模糊匹配）", example = "离心机")
    @Size(max = 100)
    private String keyword;

    @Schema(description = "文档类别 REGULATION/MANUAL/FAQ/SOP", example = "SOP")
    private String category;

    @Schema(description = "流水线状态 CREATED/…/READY/DISABLED/FAILED", example = "READY")
    private String pipelineState;

    @Schema(description = "业务启停 1 启用 / 0 停用")
    private Integer status;
}
