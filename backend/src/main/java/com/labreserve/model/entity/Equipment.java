package com.labreserve.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 设备（{@code tb_equipment}）。
 *
 * <p>设备是库存制资源：同一时段允许多人同时借用，只要 {@code Σ已借数量 ≤ stock}。
 * 状态区分「正常 / 维修中 / 报废 / 停用」，只有正常状态可被预约。
 */
@Data
@TableName("tb_equipment")
public class Equipment implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 资源编号，格式 {@code ZY-XX-YYY}（唯一） */
    private String resourceNo;

    private String name;

    private String model;

    /** 库存数量（同一时段可借出上限） */
    private Integer stock;

    /** 1 正常 / 2 维修中 / 3 报废 / 0 停用 */
    private Integer status;

    /** 所属实验室 {@code tb_laboratory.id} */
    private Long labId;

    /** 下次校准日期（手工填写，系统不推算不提醒） */
    private LocalDate nextCalibrationDate;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
