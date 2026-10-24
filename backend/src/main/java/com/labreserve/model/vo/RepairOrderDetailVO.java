package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.List;

/**
 * 工单详情：列表字段 + 故障图片 + 维修记录时间线。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "报修工单详情")
public class RepairOrderDetailVO extends RepairOrderVO {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "故障截图 URL 列表")
    private List<String> faultImages;

    @Schema(description = "维修记录（按时间升序）")
    private List<RepairRecordVO> records;
}
