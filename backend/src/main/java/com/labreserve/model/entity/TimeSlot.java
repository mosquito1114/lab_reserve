package com.labreserve.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 固定时段字典（{@code tb_time_slot}）。
 *
 * <p>预约的粒度就是一个时段（{@code tb_reservation.slot_id}），
 * 时段是全校统一的字典数据，不按实验室区分。
 */
@Data
@TableName("tb_time_slot")
public class TimeSlot implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 编码：S1 / S2 ...（唯一） */
    private String code;

    /** 名称：1-2节 / 3-4节 ... */
    private String name;

    private LocalTime startTime;

    private LocalTime endTime;

    private Integer sort;

    /** 1 启用 / 0 停用 */
    private Integer status;

    private LocalDateTime createdAt;
}
