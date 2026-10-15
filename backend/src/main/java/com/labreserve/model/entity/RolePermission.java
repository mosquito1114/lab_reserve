package com.labreserve.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 角色权限关联（{@code tb_role_permission}）。
 *
 * <p>角色与权限多对多；「角色 → 权限码」的查询是鉴权链路上最高频的操作，
 * 结果会缓存到 Redis（{@code lab:reserve:perm:role:{roleId}}）。
 */
@Data
@TableName("tb_role_permission")
public class RolePermission implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roleId;

    private Long permissionId;

    private LocalDateTime createdAt;
}
