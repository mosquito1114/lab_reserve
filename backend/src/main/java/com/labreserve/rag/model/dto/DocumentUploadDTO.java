package com.labreserve.rag.model.dto;

import com.labreserve.rag.enums.RagDocumentCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 文档上传表单字段（{@code POST /api/rag/admin/documents/upload}）。
 *
 * <p>与 {@code @RequestPart MultipartFile file} 在同一个 multipart 请求里；
 * 由 Spring 用 {@code @ModelAttribute} 绑定（无需手写每个 {@code @RequestParam}）。
 *
 * <p>字段全部可选，由后端做"文档级覆写 / 全局默认"三层回退：
 * <ul>
 *   <li>{@code chunkingStrategy} 留空 → 后端读 {@code tb_rag_document.chunking_strategy} → 仍空再读
 *       {@code lab-reserve.rag.ingest.chunk-default}</li>
 *   <li>{@code chunkMaxChars / chunkOverlap} 同理，回退顺序：文档级 → 策略自带 → 全局默认</li>
 * </ul>
 *
 * <p>必填项只剩 {@link #category}（不传会被 {@code RagDocumentCategory.requireOf} 挡回 400）。
 */
@Data
@Schema(description = "上传文档表单字段（multipart/form-data；file 单独由 @RequestPart 接收）")
public class DocumentUploadDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "文档类别（必填，REGULATION/MANUAL/FAQ/SOP）", example = "REGULATION")
    private String category;

    @Schema(description = "标题，留空则取文件名", example = "化学实验室安全手册（第 3 版）")
    @Size(max = 255, message = "标题长度不能超过 255")
    private String title;

    @Schema(description = "切片策略 code（RECURSIVE/QA/FIXED_OVERLAP），留空走全局默认",
            example = "RECURSIVE")
    @Size(max = 32)
    private String chunkingStrategy;

    @Schema(description = "切长上限（策略切长上限），留空走策略/全局默认", example = "500")
    private Integer chunkMaxChars;

    @Schema(description = "切窗重叠（QA 类策略忽略），留空走策略/全局默认", example = "100")
    private Integer chunkOverlap;

    /**
     * 把入参 category 解析为枚举（仅做大小写规范化、不做允许性校验——{@link #requireOf} 才报 400）。
     * 用于 Service 层把字符串化成 category 字段。
     */
    public RagDocumentCategory parsedCategory() {
        return RagDocumentCategory.requireOf(category);
    }
}
