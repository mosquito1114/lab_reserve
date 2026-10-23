package com.labreserve.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalTime;

/**
 * 时段新增 / 修改入参（{@code POST /api/time_slot/create|update}）。
 */
@Data
@Schema(description = "时段保存入参")
public class TimeSlotSaveDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键 ID（修改时必填）")
    private Long id;

    @Schema(description = "编码，全局唯一", example = "S2")
    @NotBlank(message = "编码不能为空")
    @Size(max = 20, message = "编码不能超过 20 个字符")
    private String code;

    @Schema(description = "名称", example = "3-4节")
    @NotBlank(message = "名称不能为空")
    @Size(max = 30, message = "名称不能超过 30 个字符")
    private String name;

    @Schema(description = "开始时间", example = "10:00")
    @NotNull(message = "开始时间不能为空")
    @JsonFormat(pattern = "HH:mm")
    private LocalTime startTime;

    @Schema(description = "结束时间", example = "11:40")
    @NotNull(message = "结束时间不能为空")
    @JsonFormat(pattern = "HH:mm")
    private LocalTime endTime;

    @Schema(description = "排序号", example = "2")
    private Integer sort;

    @Schema(description = "状态：1 启用 / 0 停用", example = "1")
    private Integer status;
}
