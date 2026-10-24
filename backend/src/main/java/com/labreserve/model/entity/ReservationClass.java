package com.labreserve.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 预约的上课班级（{@code tb_reservation_class}）。
 *
 * <p>教师预约实验室时必须指定班级；学生端「我的实验课表」就是按
 * {@code class_id = 我的班级} 反查这张表得到的。
 */
@Data
@TableName("tb_reservation_class")
public class ReservationClass implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reservationId;

    private Long classId;

    private LocalDateTime createdAt;
}
