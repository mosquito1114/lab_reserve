package com.labreserve.model.dto;

import com.labreserve.common.result.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDate;

/**
 * 预约分页查询（{@code get_page} / {@code get_my_page} / {@code get_pending_page} 共用）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "预约分页查询入参")
public class ReservationPageDTO extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "状态：PENDING / APPROVED / REJECTED / CANCELED / CHANGE_PENDING / EXPIRED")
    private String status;

    @Schema(description = "资源类型：1 实验室 / 2 设备")
    private Integer resourceType;

    @Schema(description = "实验室 ID")
    private Long labId;

    @Schema(description = "设备 ID")
    private Long equipmentId;

    @Schema(description = "预约日期起", example = "2026-10-01")
    private LocalDate reserveDateBegin;

    @Schema(description = "预约日期止", example = "2026-10-31")
    private LocalDate reserveDateEnd;

    @Schema(description = "实验类型 ID")
    private Long experimentTypeId;

    @Schema(description = "申请人学院（管理员筛选用）")
    private String college;

    @Schema(description = "申请人姓名 / 账号模糊匹配（管理员筛选用）")
    private String applicantKeyword;

    @Schema(description = "是否由排队转正：0 否 / 1 是")
    private Integer fromQueue;
}
