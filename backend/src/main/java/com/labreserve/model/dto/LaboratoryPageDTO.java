package com.labreserve.model.dto;

import com.labreserve.common.result.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 实验室分页查询（{@code POST /api/laboratory/get_page}）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "实验室分页查询入参")
public class LaboratoryPageDTO extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "关键字：编号 / 名称 / 位置 模糊匹配", example = "计算机")
    private String keyword;

    @Schema(description = "位置模糊匹配", example = "实验楼")
    private String location;

    @Schema(description = "实验类型 ID")
    private Long experimentTypeId;

    @Schema(description = "最小容纳人数", example = "30")
    private Integer minCapacity;

    @Schema(description = "状态：1 可用 / 0 停用")
    private Integer status;
}
