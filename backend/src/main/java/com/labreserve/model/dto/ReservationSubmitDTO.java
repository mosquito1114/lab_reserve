package com.labreserve.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 预约预检 / 提交入参（{@code /check} 与 {@code /create} 共用）。
 *
 * <p>角色差异由服务端强制校验：实验室只允许教师且必须带 {@code classIds}；
 * 设备师生都能约，{@code classIds} 不生效。
 */
@Data
@Schema(description = "预约提交入参")
public class ReservationSubmitDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "资源类型：1 实验室 / 2 设备", example = "1")
    @NotNull(message = "资源类型不能为空")
    @Min(value = 1, message = "资源类型只能是 1 或 2")
    @Max(value = 2, message = "资源类型只能是 1 或 2")
    private Integer resourceType;

    @Schema(description = "实验室 ID（resourceType=1 必填）")
    private Long labId;

    @Schema(description = "设备 ID（resourceType=2 必填，主资源）")
    private Long equipmentId;

    @Schema(description = "附带设备（可选，实验室顺带借设备）")
    @Valid
    private List<ReservationEquipmentDTO> equipments;

    @Schema(description = "预约日期", example = "2026-10-25")
    @NotNull(message = "预约日期不能为空")
    private LocalDate reserveDate;

    @Schema(description = "时段 ID", example = "2")
    @NotNull(message = "时段不能为空")
    private Long slotId;

    @Schema(description = "使用人数", example = "30")
    @NotNull(message = "使用人数不能为空")
    @Min(value = 1, message = "使用人数至少为 1")
    private Integer usePeople;

    @Schema(description = "实验类型 ID（实验室必须等于该实验室的类型）", example = "2")
    @NotNull(message = "实验类型不能为空")
    private Long experimentTypeId;

    @Schema(description = "上课班级 ID 列表（教师预约实验室必填）")
    private List<Long> classIds;

    @Schema(description = "授课课程（教师必填）", example = "操作系统实验")
    @Size(max = 100, message = "课程名不能超过 100 个字符")
    private String courseName;

    @Schema(description = "实验内容 / 用途", example = "进程调度实验")
    @NotBlank(message = "实验内容不能为空")
    @Size(max = 500, message = "实验内容不能超过 500 个字符")
    private String purpose;
}
