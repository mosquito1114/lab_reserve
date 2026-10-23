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
 * 实验室（{@code tb_laboratory}）。
 *
 * <p>只有教师能为实验室排课（见 {@code design.md} §7.8），
 * 所以「可开展实验类型」直接内联为外键，不再建关联表。
 */
@Data
@TableName("tb_laboratory")
public class Laboratory implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 实验室编号，格式 {@code LAB-XX}（唯一） */
    private String labNo;

    private String name;

    private String location;

    /** 开放开始时间，如 08:00 */
    private LocalTime openTimeStart;

    /** 开放结束时间，如 18:00 */
    private LocalTime openTimeEnd;

    /** 开放星期，逗号分隔，1=周一：{@code 1,2,3,4,5} */
    private String openWeekdays;

    /** 容纳人数 */
    private Integer capacity;

    /** 可开展实验类型 {@code tb_experiment_type.id} */
    private Long experimentTypeId;

    /** 1 可用 / 0 停用 */
    private Integer status;

    private String description;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
