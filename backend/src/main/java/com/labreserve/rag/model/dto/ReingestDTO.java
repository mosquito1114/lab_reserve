package com.labreserve.rag.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 重建索引入参（{@code POST /api/rag/admin/documents/{id}/reingest}）。
 *
 * <p>重建语义（见 design.md §3.7）：
 * <ul>
 *   <li>{@code fileUrl=null} + 其它切片参数都 null → 仅版本号+1、全链重跑（管理员确信旧 file_url 仍有效）</li>
 *   <li>{@code chunkingStrategy / chunkMaxChars / chunkOverlap} 任一非空 → 该字段会落到 document 表，CHUNK Step 重新读取</li>
 *   <li>{@code fileUrl} 非空 → 走"替换文件"路径，假定外层已经上传新版本到 OSS 并得到 URL</li>
 * </ul>
 */
@Data
@Schema(description = "重建索引入参")
public class ReingestDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "新文件 OSS URL（为空则不替换文件，仅重跑状态机）",
            example = "https://rag-lab-kb.oss-cn-beijing.aliyuncs.com/lab-reserve/RAG_DOC/2025/10/<uuid>.pdf")
    @Size(max = 500)
    private String fileUrl;

    @Schema(description = "新切片策略 code（覆盖文档级值）", example = "QA")
    @Size(max = 32)
    private String chunkingStrategy;

    @Schema(description = "新切长上限（覆盖文档级值）", example = "800")
    private Integer chunkMaxChars;

    @Schema(description = "新切窗重叠（覆盖文档级值；QA 类忽略）", example = "150")
    private Integer chunkOverlap;

    @Schema(description = "重建原因，会作为步骤日志的备注", example = "替换版本 V2")
    @Size(max = 500)
    private String reason;
}
