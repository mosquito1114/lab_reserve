package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 文件上传结果（阿里云 OSS）。
 */
@Data
@Schema(description = "文件上传结果")
public class FileUploadVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "可访问 URL")
    private String url;

    @Schema(description = "对象 Key（可用于删除）")
    private String objectKey;

    @Schema(description = "原始文件名")
    private String name;

    @Schema(description = "字节数")
    private Long size;

    @Schema(description = "MIME 类型")
    private String contentType;
}
