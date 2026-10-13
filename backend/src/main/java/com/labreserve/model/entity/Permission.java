package com.labreserve.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 权限点（{@code tb_permission}）。
 *
 * <p>{@code code} 命名规范为 {@code 模块:资源:动作}，例如 {@code reservation:audit}。
 * 权限是「数据」而不是代码：系统管理员在界面上勾选即可生效（精简 RBAC，见 {@code design.md} K2 方案 C）。
 */
@Data
@TableName("tb_permission")
public class Permission implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 权限码，如 {@code reservation:audit} */
    private String code;

    private String name;

    /** 所属模块，用于权限勾选树分组：user / resource / dict / class / reservation / ... */
    private String module;

    private String description;

    private LocalDateTime createdAt;
}
