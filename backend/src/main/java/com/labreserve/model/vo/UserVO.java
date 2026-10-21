package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户列表项（{@code POST /api/user/get_page}）。
 *
 * <p>一行要同时容纳三类角色的扩展信息，因此学生 / 教师 / 员工的字段并存，
 * 不适用的字段为 {@code null}（前端按 {@code roleCode} 决定展示哪几列）。
 *
 * <p>这些字段全部来自 JOIN（反冗余）：{@code studentNo}/{@code className} 来自
 * {@code tb_student_info + tb_class}，{@code college} 取学生班级或教师所属学院，
 * {@code staffNo}/{@code department} 来自 {@code tb_staff_info}。
 */
@Data
@Schema(description = "用户列表项")
public class UserVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String username;

    private String realName;

    @Schema(description = "角色编码", example = "STUDENT")
    private String roleCode;

    private String roleName;

    /** 手机号：默认脱敏，revealPhone=true 且有权限时为明文 */
    private String phone;

    private String email;

    private Integer notifyEmail;

    /** 1 正常 / 0 已注销 */
    private Integer status;

    private LocalDateTime lastLoginAt;

    private LocalDateTime createdAt;

    // ---------- 学生 ----------
    private String studentNo;

    private Long classId;

    private String className;

    // ---------- 教师 ----------
    private String teacherNo;

    // ---------- 管理类 ----------
    private String staffNo;

    private String department;

    /** 维修人员类别：1 校内 / 2 校外 */
    private Integer maintainerType;

    // ---------- 公共展示 ----------
    /** 学院：学生取班级的学院，教师取 teacher_info.college */
    private String college;

    /** 年级（学生） */
    private Integer grade;
}
