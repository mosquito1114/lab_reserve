package com.labreserve.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 派单（{@code POST /api/repair_order/assign}）。
 */
@Data
@Schema(description = "派单入参")
public class RepairAssignDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "工单 ID 不能为空")
    private Long id;

    @Schema(description = "维修人员 user.id（必须 roleCode=MAINTAINER 且状态正常）")
    @NotNull(message = "请选择维修人员")
    private Long assigneeId;

    @Schema(description = "派单备注", example = "请今天内上门")
    @Size(max = 255, message = "备注不能超过 255 个字符")
    private String opinion;
}
