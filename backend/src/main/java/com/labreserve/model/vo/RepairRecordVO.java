package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 维修进度记录。
 */
@Data
@Schema(description = "维修记录")
public class RepairRecordVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long orderId;

    private Long maintainerId;

    private String maintainerName;

    @Schema(description = "1 维修中 / 2 已完成")
    private Integer status;

    private String statusName;

    private String repairDesc;

    private String partsReplaced;

    private LocalDateTime createdAt;
}
