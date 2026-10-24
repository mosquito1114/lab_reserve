package com.labreserve.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 候补通知记录（{@code tb_candidate_notice_log}）。
 *
 * <p>一条 {@code action=0} 的记录代表「正在等待这个人的确认」，
 * 它是<b>顺延的幂等依据</b>（同一 queue 同一轮只通知一次）与超时扫描的依据
 * （{@code deadline} + {@code action=0}）。
 */
@Data
@TableName("tb_candidate_notice_log")
public class CandidateNoticeLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 0 未响应 / 1 确认 / 2 放弃 / 3 超时 */
    public static final int ACTION_PENDING = 0;
    public static final int ACTION_CONFIRMED = 1;
    public static final int ACTION_ABANDONED = 2;
    public static final int ACTION_TIMEOUT = 3;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long queueId;

    private LocalDateTime noticeTime;

    /** 确认截止时间（默认 now + candidate-timeout-minutes） */
    private LocalDateTime deadline;

    /** 1 站内信 / 2 邮件（本项目统一发两者，这里始终写 1 表示主通道） */
    private Integer channel;

    private Integer action;

    private LocalDateTime actionTime;

    /** 该排队记录第几次被通知（顺延后会再次通知） */
    private Integer seq;

    private LocalDateTime createdAt;
}
