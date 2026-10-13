package com.labreserve.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 角色（{@code tb_role}）。
 *
 * <p>五个固定角色，数据由种子脚本灌入，界面上只能改「角色拥有哪些权限」，不能增删角色。
 */
@Data
@TableName("tb_role")
public class Role implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** STUDENT / TEACHER / SYS_ADMIN / LAB_ADMIN / MAINTAINER */
    private String code;

    private String name;

    private String description;

    private LocalDateTime createdAt;
}
