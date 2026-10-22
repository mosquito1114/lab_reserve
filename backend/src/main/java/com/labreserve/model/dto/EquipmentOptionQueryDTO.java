package com.labreserve.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 设备下拉查询（{@code POST /api/equipment/list_option}）。
 */
@Data
@Schema(description = "设备下拉查询入参")
public class EquipmentOptionQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "所属实验室 ID（为空表示全部）")
    private Long labId;

    @Schema(description = "是否只要可预约的（状态正常）；默认 true", example = "true")
    private Boolean onlyAvailable;
}
