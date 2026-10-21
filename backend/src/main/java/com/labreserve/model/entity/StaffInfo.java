package com.labreserve.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 管理类人员扩展信息（{@code tb_staff_info}）。
 *
 * <p>系统管理员 / 实验室管理员 / 维修人员共用；<b>没有 staff_type 字段</b>，
 * 三类人员由 {@code tb_user.role_id} 区分（{@code design.md} D3）。
 */
@Data
@TableName("tb_staff_info")
public class StaffInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** → tb_user.id（唯一） */
    private Long userId;

    /** 工号（唯一） */
    private String staffNo;

    private String department;

    /** 维修人员类别：1 校内 / 2 校外（仅维修人员有值） */
    private Integer maintainerType;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
