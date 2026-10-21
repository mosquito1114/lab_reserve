package com.labreserve.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 教师扩展信息（{@code tb_teacher_info}）。
 */
@Data
@TableName("tb_teacher_info")
public class TeacherInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** → tb_user.id（唯一） */
    private Long userId;

    /** 工号（唯一） */
    private String teacherNo;

    private String college;

    /** 职称 */
    private String title;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
