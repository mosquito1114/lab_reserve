package com.labreserve.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 班级（{@code tb_class}）。
 *
 * <p>班级是<b>学生学院 / 专业的唯一来源</b>（{@code tb_student_info} 只存 class_id），
 * 同时是教师排课时选择上课班级、学生查看「我的实验课表」的匹配依据。
 */
@Data
@TableName("tb_class")
public class ClassInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 班级名称，如 软工2101（全局唯一） */
    private String className;

    private String college;

    private String major;

    /** 年级，如 2021 */
    private Integer grade;

    /** 班级人数（教务上的规模，不等于本系统账号数） */
    private Integer studentCount;

    /** 1 正常 / 0 停用 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
