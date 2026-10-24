package com.labreserve.service;

import com.labreserve.common.result.PageResult;
import com.labreserve.model.dto.QueueCreateDTO;
import com.labreserve.model.dto.QueuePageDTO;
import com.labreserve.model.dto.QueuePositionDTO;
import com.labreserve.model.vo.QueueBanVO;
import com.labreserve.model.vo.QueueConfirmVO;
import com.labreserve.model.vo.QueueNoticeVO;
import com.labreserve.model.vo.QueuePositionVO;
import com.labreserve.model.vo.QueueVO;

import java.util.List;

/**
 * 排队与候补服务（迭代 6）。
 *
 * <p>队列本身存在 Redis ZSet（{@code queue:{type}:{resId}:{date}:{slotId}}，member = userId，
 * score = 入队时间戳），DB 只做持久化与状态机承载；
 * <b>顺位一律实时算</b>（{@code ZRANK + 1}），不落库。
 */
public interface QueueService {

    /** 加入排队（锁 + ZADD NX 幂等 + 排队号） */
    QueueVO create(QueueCreateDTO dto);

    /** 我的排队记录（每条带实时顺位） */
    PageResult<QueueVO> myPage(QueuePageDTO dto);

    /** 某资源的完整队列（管理员） */
    PageResult<QueueVO> page(QueuePageDTO dto);

    /** 排队详情 */
    QueueVO getInfo(Long id);

    /** 实时顺位（轻量轮询） */
    QueuePositionVO myPosition(QueuePositionDTO dto);

    /** 取消排队（仅 QUEUING，不写禁入） */
    void cancel(Long id);

    /** 候补确认：直接生成 APPROVED 预约单 */
    QueueConfirmVO confirm(Long id);

    /** 放弃候补：写 7 天禁入并顺延下一位 */
    void abandon(Long id, String reason);

    /** 候补通知历史 */
    List<QueueNoticeVO> noticeList(Long queueId);

    /** 我的禁入记录 */
    List<QueueBanVO> myBanList(Boolean activeOnly);
}
