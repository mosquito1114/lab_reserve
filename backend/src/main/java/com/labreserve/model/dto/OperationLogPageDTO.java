package com.labreserve.model.dto;

import com.labreserve.common.result.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 操作日志分页查询（{@code POST /api/operation_log/get_page}）。
 *
 * <p>对应 {@code api.md} §10.3 的筛选条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "操作日志分页查询入参")
public class OperationLogPageDTO extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "操作类型：LOGIN / LOGOUT / CREATE / UPDATE / DELETE / AUDIT / EXPORT / ASSIGN / ACCEPT / QUERY_PHONE",
            example = "AUDIT")
    private String operationType;

    @Schema(description = "所属模块：auth / user / resource / reservation / queue / repair / message / statistics / system",
            example = "system")
    private String module;

    @Schema(description = "操作人 ID")
    private Long operatorId;

    @Schema(description = "操作人姓名/账号模糊匹配（JOIN tb_user）", example = "李管理员")
    private String operatorKeyword;

    @Schema(description = "开始时间", example = "2025-09-01 00:00:00")
    private LocalDateTime beginTime;

    @Schema(description = "结束时间", example = "2025-09-30 23:59:59")
    private LocalDateTime endTime;

    @Schema(description = "客户端 IP（模糊匹配）")
    private String ip;

    @Schema(description = "结果：1 成功 / 0 失败")
    private Integer result;
}
