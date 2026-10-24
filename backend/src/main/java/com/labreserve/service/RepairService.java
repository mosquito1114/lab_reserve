package com.labreserve.service;

import com.labreserve.common.result.PageResult;
import com.labreserve.model.dto.RepairAcceptDTO;
import com.labreserve.model.dto.RepairAssignDTO;
import com.labreserve.model.dto.RepairCreateDTO;
import com.labreserve.model.dto.RepairPageDTO;
import com.labreserve.model.dto.RepairProgressDTO;
import com.labreserve.model.dto.RepairReviewDTO;
import com.labreserve.model.vo.RepairOrderDetailVO;
import com.labreserve.model.vo.RepairOrderVO;
import com.labreserve.model.vo.RepairRecordVO;

import java.util.List;

/**
 * 设备报修服务（迭代 7）。
 *
 * <p>状态机：{@code SUBMITTED → (复核) → ASSIGNED → REPAIRING → REPAIRED → ACCEPTED}，
 * 复核驳回走 {@code REJECTED}，验收不通过走 {@code REOPENED}（可再次维修）。
 * 非法流转一律 409；维修人只能操作指派给自己的工单（403）。
 *
 * <p>需求 Q23：维修<b>不联动</b>作废任何预约。
 */
public interface RepairService {

    /** 提交报修（自动通知所有实验室管理员） */
    RepairOrderVO create(RepairCreateDTO dto);

    /** 工单分页（管理员 / 报修人看自己的） */
    PageResult<RepairOrderVO> page(RepairPageDTO dto);

    /** 我的维修任务（维修人） */
    PageResult<RepairOrderVO> myAssignedPage(RepairPageDTO dto);

    /** 详情（含维修记录时间线） */
    RepairOrderDetailVO getInfo(Long id);

    /** 复核：确认故障（可改优先级 + 设备状态）/ 驳回 */
    void review(RepairReviewDTO dto);

    /** 派单 / 改派 */
    void assign(RepairAssignDTO dto);

    /** 维修人更新进度（工单进入 REPAIRING） */
    void updateProgress(RepairProgressDTO dto);

    /** 维修完成（写维修说明 + 配件，工单进入 REPAIRED，通知管理员验收） */
    void complete(RepairProgressDTO dto);

    /** 验收：通过 → ACCEPTED + 归档 + 设备恢复正常；不通过 → REOPENED */
    void accept(RepairAcceptDTO dto);

    /** 某工单的维修记录 */
    List<RepairRecordVO> recordList(Long orderId);
}
