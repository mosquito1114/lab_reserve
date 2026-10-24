package com.labreserve.service;

import com.labreserve.common.result.PageResult;
import com.labreserve.model.dto.ReservationChangeAuditDTO;
import com.labreserve.model.dto.ReservationChangeCreateDTO;
import com.labreserve.model.dto.ReservationChangePageDTO;
import com.labreserve.model.vo.ReservationChangeVO;

import java.util.List;

/**
 * 预约调整申请服务。
 *
 * <p>核心约定（{@code design.md} §4.2）：<b>审批通过前，预约单的业务字段一个都不改</b>，
 * 只把 {@code tb_reservation.status} 置为 {@code CHANGE_PENDING}；
 * 真正的回写发生在审批通过时，且必须在同一个分布式锁里完成。
 */
public interface ReservationChangeService {

    /** 发起调整申请（24h 窗口 + before/after 快照 + 只改 status） */
    Long create(ReservationChangeCreateDTO dto);

    /** 某预约的调整历史 */
    List<ReservationChangeVO> listByReservation(Long reservationId);

    /** 调整申请分页（管理员） */
    PageResult<ReservationChangeVO> page(ReservationChangePageDTO dto);

    /** 审批：通过时锁内校验新时段 → 回写 → 改状态 → 发消息 */
    void audit(ReservationChangeAuditDTO dto);

    /** 撤销自己的待审申请（预约回到 APPROVED） */
    void cancel(Long id);
}
