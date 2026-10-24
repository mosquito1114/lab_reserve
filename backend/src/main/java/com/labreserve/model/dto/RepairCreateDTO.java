package com.labreserve.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 提交报修（{@code POST /api/repair_order/create}）。
 */
@Data
@Schema(description = "提交报修入参")
public class RepairCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "请选择报修设备")
    private Long equipmentId;

    @Schema(description = "故障现象", example = "通电后屏幕无显示，风扇不转")
    @NotBlank(message = "请填写故障现象")
    @Size(max = 1000, message = "故障现象不能超过 1000 个字符")
    private String faultDesc;

    @Schema(description = "发生场景", example = "2026-10-26 上午第三节课，做操作系统实验时")
    @NotBlank(message = "请填写发生场景")
    @Size(max = 255, message = "发生场景不能超过 255 个字符")
    private String faultScene;

    @Schema(description = "故障截图 URL（最多 5 张）")
    @Size(max = 5, message = "最多上传 5 张图片")
    private List<String> faultImages;
}
