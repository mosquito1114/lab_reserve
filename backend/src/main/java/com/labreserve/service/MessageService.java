package com.labreserve.service;

import com.labreserve.common.enums.MessageType;
import com.labreserve.common.result.PageResult;
import com.labreserve.model.dto.MessageDeliveryLogPageDTO;
import com.labreserve.model.dto.MessagePageDTO;
import com.labreserve.model.vo.MessageDeliveryLogVO;
import com.labreserve.model.vo.MessageVO;

import java.util.List;

/**
 * 消息服务。
 *
 * <p>{@link #send} 是<b>全项目统一发消息入口</b>：
 * 其他模块（预约、排队、运维）只调它，不直接操作 {@code tb_message} 或 Redis，
 * 这样「站内信 + 收件箱索引 + 未读数 + 邮件入队」四个动作永远是一起的。
 */
public interface MessageService {

    /**
     * 发一条消息给指定用户。
     *
     * <p>动作：写 {@code tb_message} → {@code ZADD inbox} → {@code INCR unread} →
     * 接收人开启邮件通知且邮箱非空时置 {@code email_required=1} 并 {@code XADD msg:email}。
     *
     * @param receiverId 接收人 {@code tb_user.id}
     * @param type       消息类型
     * @param title      标题（邮件主题会拼成「【系统名】+标题」）
     * @param content    正文（同时是渲染邮件的唯一来源）
     * @param bizType    关联业务类型，可为 null
     * @param bizId      关联业务 ID，可为 null
     * @return 新消息 ID
     */
    Long send(Long receiverId, MessageType type, String title, String content, String bizType, Long bizId);

    /** 收件箱分页（Redis ZSet 顺序 = 时间倒序） */
    PageResult<MessageVO> page(MessagePageDTO dto);

    /** 消息详情（自动置已读） */
    MessageVO getInfo(Long id);

    /** 未读数（铃铛角标） */
    long unreadCount();

    /** 单条已读 */
    void read(Long id);

    /** 全部已读 */
    void readAll();

    /** 删除单条（用户视角软删除） */
    void delete(Long id);

    /** 一键清空 */
    void clear();

    /** 邮件投递记录分页（管理员排查用） */
    PageResult<MessageDeliveryLogVO> deliveryLogPage(MessageDeliveryLogPageDTO dto);

    /** 发送测试邮件（联调 SMTP） */
    void sendTestEmail(String to);

    /**
     * 发送系统通知（管理员主动发，需 {@code message:send}）。
     *
     * @return 实际发出的条数
     */
    int sendSystem(List<Long> receiverIds, String title, String content);
}
