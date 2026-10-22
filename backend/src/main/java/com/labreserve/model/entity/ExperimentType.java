package com.labreserve.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实验类型字典（{@code tb_experiment_type}）。
 *
 * <p>一个实验室只开展一种固定类型的实验（{@code tb_laboratory.experiment_type_id}），
 * 预约时也会记录实验类型，用于统计「各实验类型占比」。
 */
@Data
@TableName("tb_experiment_type")
public class ExperimentType implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 编码：DEMO / VALIDATE / COMPREHENSIVE / DESIGN / INNOVATE（唯一） */
    private String code;

    /** 名称：演示性 / 验证性 / 综合性 / 设计性 / 创新性 */
    private String name;

    /** 排序号，小的在前 */
    private Integer sort;

    /** 1 启用 / 0 停用 */
    private Integer status;

    private LocalDateTime createdAt;
}
