package com.labreserve.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 资源更新日志（{@code tb_resource_update_log}）。
 *
 * <p>实验室 / 设备的每次新增、修改、停用都强制填写「更新原因」，
 * 并留下 before / after 快照，便于追溯「谁在什么时候把容量从 60 改成了 80」。
 */
@Data
@TableName("tb_resource_update_log")
public class ResourceUpdateLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 1 实验室 / 2 设备 */
    private Integer resourceType;

    private Long resourceId;

    /** 1 新增 / 2 修改 / 3 删除（停用/启用也算状态变更） */
    private Integer operation;

    /** 变更前快照（JSON，新增时为空） */
    private String beforeJson;

    /** 变更后快照（JSON，停用时也保留最后状态） */
    private String afterJson;

    /** 更新原因（强制填写） */
    private String updateReason;

    private Long operatorId;

    private LocalDateTime createdAt;
}
