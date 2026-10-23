package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.List;

/**
 * 实验室详情：列表字段 + 关联设备。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "实验室详情")
public class LaboratoryDetailVO extends LaboratoryVO {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "该实验室下的设备（下拉结构）")
    private List<EquipmentOptionVO> equipments;
}
