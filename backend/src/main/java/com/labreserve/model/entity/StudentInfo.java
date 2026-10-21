package com.labreserve.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 学生扩展信息（{@code tb_student_info}）。
 *
 * <p>只存 {@code student_no} 与 {@code class_id}：学院 / 专业 / 班级名都从
 * {@link ClassInfo} 带出，避免双真相源（{@code design.md} D10）。
 */
@Data
@TableName("tb_student_info")
public class StudentInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** → tb_user.id（唯一） */
    private Long userId;

    /** 学号（唯一） */
    private String studentNo;

    /** → tb_class.id */
    private Long classId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
