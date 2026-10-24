package com.labreserve.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实验室预约附带的设备清单（{@code tb_reservation_equipment}）。
 *
 * <p>设备预约（{@code resourceType=2}）的主资源在 {@code tb_reservation.equipment_id}，
 * 这张表只承载「实验室顺带借的设备」，调整申请里的「增减设备」也落在它上面。
 */
@Data
@TableName("tb_reservation_equipment")
public class ReservationEquipment implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reservationId;

    private Long equipmentId;

    private Integer equipmentCount;

    private LocalDateTime createdAt;
}
