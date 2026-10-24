package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 预约附带的设备。
 */
@Data
@Schema(description = "预约附带设备")
public class ReservationEquipmentVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long equipmentId;

    private String resourceNo;

    private String name;

    private String model;

    private Integer equipmentCount;
}
