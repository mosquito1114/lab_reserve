package com.labreserve.service;

import com.labreserve.common.result.PageResult;
import com.labreserve.model.dto.ReservationAuditDTO;
import com.labreserve.model.dto.ReservationCancelDTO;
import com.labreserve.model.dto.ReservationPageDTO;
import com.labreserve.model.dto.ReservationSubmitDTO;
import com.labreserve.model.dto.ReservationTimetableDTO;
import com.labreserve.model.vo.ReservationCheckVO;
import com.labreserve.model.vo.ReservationDetailVO;
import com.labreserve.model.vo.ReservationVO;
import com.labreserve.model.vo.TimetableVO;

import java.util.List;

/**
 * 预约服务（迭代 5 核心）。
 *
 * <p>并发约定：{@link #create} / {@link #audit} / {@link #release} 都在
 * {@code lab:reserve:lock:reserve:{type}:{resId}:{date}:{slotId}} 锁内执行，
 * 并由 {@code tb_reservation} 的生成列唯一索引 {@code uk_reservation_lab_occupy} 兜底。
 */
public interface ReservationService {

    /** 预检（只读）：返回可约 / 冲突码 / 排队人数 / 禁入信息 */
    ReservationCheckVO check(ReservationSubmitDTO dto);

    /** 提交预约（加锁 + 唯一索引兜底），返回详情 */
    ReservationDetailVO create(ReservationSubmitDTO dto);

    /** 全量分页（管理员用，需 reservation:list-all） */
    PageResult<ReservationVO> page(ReservationPageDTO dto);

    /** 我的预约（只看本人） */
    PageResult<ReservationVO> myPage(ReservationPageDTO dto);

    /** 待审核列表（PENDING） */
    PageResult<ReservationVO> pendingPage(ReservationPageDTO dto);

    /** 详情（本人或具备 reservation:view） */
    ReservationDetailVO getInfo(Long id);

    /** 审核：通过 / 驳回（锁内二次判冲 + 发消息） */
    void audit(ReservationAuditDTO dto);

    /** 撤销（仅本人 + 仅 PENDING） */
    void cancel(ReservationCancelDTO dto);

    /** 管理员释放时段（PENDING/APPROVED → CANCELED + 发消息；候补顺延在迭代 6） */
    void release(ReservationCancelDTO dto);

    /** 我的实验课表（学生按班级反查） */
    List<TimetableVO> timetable(ReservationTimetableDTO dto);
}
