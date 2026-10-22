package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 资源更新日志（含 before / after 快照）。
 */
@Data
@Schema(description = "资源更新日志")
public class ResourceLogVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    @Schema(description = "1 实验室 / 2 设备")
    private Integer resourceType;

    private String resourceTypeName;

    private Long resourceId;

    @Schema(description = "资源编号（LAB-XX / ZY-XX-YYY）")
    private String resourceNo;

    @Schema(description = "资源名称（取资源表当前值）")
    private String resourceName;

    @Schema(description = "1 新增 / 2 修改 / 3 状态变更")
    private Integer operation;

    private String operationName;

    @Schema(description = "变更前快照（JSON）")
    private String beforeJson;

    @Schema(description = "变更后快照（JSON）")
    private String afterJson;

    private String updateReason;

    private Long operatorId;

    @Schema(description = "操作人姓名")
    private String operatorName;

    private LocalDateTime createdAt;
}
