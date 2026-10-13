package com.labreserve.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户（{@code tb_user}）。
 *
 * <p>五类角色共用这一张表，靠 {@code role_id} 区分（{@code design.md} D3）。
 * 角色独有的字段放在 {@code tb_student_info} / {@code tb_teacher_info} / {@code tb_staff_info}。
 *
 * <p>{@code phone} 明文存储，接口层统一脱敏（{@code design.md} §7.6）。
 */
@Data
@TableName("tb_user")
public class User implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录账号（学号 / 工号 / 管理员账号），全局唯一 */
    private String username;

    /** BCrypt 哈希 */
    private String password;

    private String realName;

    /** 角色 ID → {@code tb_role.id}，角色划分的唯一依据 */
    private Long roleId;

    /** 手机号（明文存储，接口层脱敏） */
    private String phone;

    private String email;

    /** 是否开启邮件通知：0 否 / 1 是 */
    private Integer notifyEmail;

    /** 状态：1 正常 / 0 已注销（不物理删除） */
    private Integer status;

    private LocalDateTime lastLoginAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** 账号是否可用（未注销） */
    public boolean isActive() {
        return status != null && status == 1;
    }
}
