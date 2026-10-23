package com.labreserve.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalTime;

/**
 * 实验室新增 / 修改入参。
 *
 * <p>{@code updateReason} 是<b>强制</b>的：资源变更必须留痕（{@code tb_resource_update_log}）。
 */
@Data
@Schema(description = "实验室保存入参")
public class LaboratorySaveDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键 ID（修改时必填）")
    private Long id;

    @Schema(description = "实验室编号，格式 LAB-XX", example = "LAB-01")
    @NotBlank(message = "实验室编号不能为空")
    @Pattern(regexp = "^LAB-\\d{2}$", message = "实验室编号格式应为 LAB-两位数字，如 LAB-01")
    private String labNo;

    @Schema(description = "名称", example = "计算机基础实验室")
    @NotBlank(message = "名称不能为空")
    @Size(max = 50, message = "名称不能超过 50 个字符")
    private String name;

    @Schema(description = "位置", example = "实验楼 A301")
    @NotBlank(message = "位置不能为空")
    @Size(max = 100, message = "位置不能超过 100 个字符")
    private String location;

    @Schema(description = "开放开始时间", example = "08:00")
    @NotNull(message = "开放开始时间不能为空")
    @JsonFormat(pattern = "HH:mm")
    private LocalTime openTimeStart;

    @Schema(description = "开放结束时间", example = "18:00")
    @NotNull(message = "开放结束时间不能为空")
    @JsonFormat(pattern = "HH:mm")
    private LocalTime openTimeEnd;

    @Schema(description = "开放星期，逗号分隔，1=周一", example = "1,2,3,4,5")
    @NotBlank(message = "开放星期不能为空")
    @Size(max = 20, message = "开放星期过长")
    private String openWeekdays;

    @Schema(description = "容纳人数", example = "60")
    @NotNull(message = "容纳人数不能为空")
    @Min(value = 1, message = "容纳人数至少为 1")
    private Integer capacity;

    @Schema(description = "可开展实验类型 ID", example = "2")
    @NotNull(message = "实验类型不能为空")
    private Long experimentTypeId;

    @Schema(description = "状态：1 可用 / 0 停用（新增默认 1）", example = "1")
    private Integer status;

    @Schema(description = "描述", example = "配备 60 台微型计算机")
    @Size(max = 255, message = "描述不能超过 255 个字符")
    private String description;

    @Schema(description = "更新原因（必填，会写入资源更新日志）", example = "新增实验室")
    @NotBlank(message = "更新原因不能为空")
    @Size(max = 255, message = "更新原因不能超过 255 个字符")
    private String updateReason;
}
