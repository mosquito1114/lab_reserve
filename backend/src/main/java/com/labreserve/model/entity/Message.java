package com.labreserve.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 消息（{@code tb_message}）。
 *
 * <p>站内信本体 + 邮件终态共用一张表：
 * 站内信部分（title / content / is_read）是邮件的渲染来源，
 * 邮件状态（email_required / email_state / email_last_error）由 Stream 消费者回写。
 *
 * <p>删除是「用户视角」的软删除（{@code is_deleted=1}），
 * 30 天前的已删消息由定时任务物理清理（迭代 3 只做软删）。
 */
@Data
@TableName("tb_message")
public class Message implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收人 tb_user.id */
    private Long receiverId;

    /** 消息类型，取值见 {@link com.labreserve.common.enums.MessageType} */
    private String type;

    private String title;

    /** 正文（同时是渲染邮件的唯一来源） */
    private String content;

    /** 关联业务类型：RESERVATION / QUEUE / REPAIR_ORDER */
    private String bizType;

    /** 关联业务 ID */
    private Long bizId;

    /** 是否需要邮件投递：0 否 1 是 */
    private Integer emailRequired;

    /** 邮件状态，取值见 {@link com.labreserve.common.enums.EmailState}，NULL 表示未入队 */
    private String emailState;

    /** 最后一次投递失败原因 */
    private String emailLastError;

    /** 0 未读 1 已读 */
    private Integer isRead;

    /** 0 正常 1 已删除（用户视角） */
    private Integer isDeleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
