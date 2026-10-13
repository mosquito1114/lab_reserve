package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志列表项 / 详情（{@code POST /api/operation_log/get_page|get_info}）。
 *
 * <p>反冗余：库里只存 {@code operator_id}，{@code operatorName} / {@code roleCode} 由查询时
 * JOIN {@code tb_user} / {@code tb_role} 得到（{@code design.md} §1.6）。
 */
@Data
@Schema(description = "操作日志")
public class OperationLogVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long operatorId;

    @Schema(description = "操作人姓名；登录失败等场景为空")
    private String operatorName;

    @Schema(description = "操作人角色编码")
    private String roleCode;

    @Schema(description = "模块编码", example = "reservation")
    private String module;

    @Schema(description = "模块中文名", example = "预约管理")
    private String moduleName;

    @Schema(description = "操作类型", example = "AUDIT")
    private String operationType;

    @Schema(description = "操作类型中文名", example = "审核")
    private String operationTypeName;

    private String content;

    private String requestMethod;

    private String requestUrl;

    /** 请求参数（已脱敏） */
    private String requestParam;

    private String ip;

    private String userAgent;

    @Schema(description = "1 成功 / 0 失败")
    private Integer result;

    private String errorMsg;

    private Integer costMs;

    private LocalDateTime createdAt;
}
