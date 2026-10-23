package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户详情（{@code POST /api/user/get_info} 与 {@code get_self_profile}）。
 *
 * <p>三类扩展信息只会有其中一块非空，前端按 {@code roleCode} 渲染。
 */
@Data
@Schema(description = "用户详情")
public class UserDetailVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String username;

    private String realName;

    private String roleCode;

    private String roleName;

    /** 手机号：默认脱敏，revealPhone=true 且有权限时为明文（会写 QUERY_PHONE 日志） */
    private String phone;

    private String email;

    private Integer notifyEmail;

    /** 1 正常 / 0 已注销 */
    private Integer status;

    private LocalDateTime lastLoginAt;

    private LocalDateTime createdAt;

    private StudentInfoVO studentInfo;

    private TeacherInfoVO teacherInfo;

    private StaffInfoVO staffInfo;
}
