package com.labreserve.controller;

import com.labreserve.common.annotation.OpLog;
import com.labreserve.common.constant.PermissionConst;
import com.labreserve.common.enums.OperationModule;
import com.labreserve.common.enums.OperationType;
import com.labreserve.common.result.PageResult;
import com.labreserve.common.result.Result;
import com.labreserve.model.dto.IdDTO;
import com.labreserve.model.dto.ReservationChangeAuditDTO;
import com.labreserve.model.dto.ReservationChangeCreateDTO;
import com.labreserve.model.dto.ReservationChangePageDTO;
import com.labreserve.model.vo.ReservationChangeVO;
import com.labreserve.security.RequirePerm;
import com.labreserve.service.ReservationChangeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 预约调整申请接口（{@code api.md} §5）。
 */
@RestController
@RequestMapping("/api/reservation_change")
@Tag(name = "07-预约管理")
public class ReservationChangeController {

    @Autowired
    private ReservationChangeService reservationChangeService;

    @Operation(summary = "发起调整申请", description = "24h 窗口 + before/after 快照；预约只改状态为 CHANGE_PENDING")
    @RequirePerm(PermissionConst.CHANGE_CREATE)
    @OpLog(module = OperationModule.RESERVATION, type = OperationType.CREATE, content = "发起预约调整申请")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody @Valid ReservationChangeCreateDTO dto) {
        return Result.ok(reservationChangeService.create(dto));
    }

    @Operation(summary = "某预约的调整历史")
    @PostMapping("/get_list_by_reservation")
    public Result<List<ReservationChangeVO>> listByReservation(@RequestBody @Valid IdDTO dto) {
        return Result.ok(reservationChangeService.listByReservation(dto.getId()));
    }

    @Operation(summary = "调整申请分页", description = "管理员查看待审批 / 已处理")
    @RequirePerm(PermissionConst.CHANGE_LIST)
    @PostMapping("/get_page")
    public Result<PageResult<ReservationChangeVO>> getPage(@RequestBody ReservationChangePageDTO dto) {
        return Result.ok(reservationChangeService.page(dto));
    }

    @Operation(summary = "审批调整申请", description = "通过时锁内校验新时段 → 回写 → 发消息")
    @RequirePerm(PermissionConst.CHANGE_AUDIT)
    @OpLog(module = OperationModule.RESERVATION, type = OperationType.AUDIT, content = "审批预约调整")
    @PostMapping("/audit")
    public Result<Void> audit(@RequestBody @Valid ReservationChangeAuditDTO dto) {
        reservationChangeService.audit(dto);
        return Result.ok();
    }

    @Operation(summary = "撤销调整申请", description = "申请人撤销自己的待审申请，预约回到已通过")
    @RequirePerm(PermissionConst.CHANGE_CREATE)
    @OpLog(module = OperationModule.RESERVATION, type = OperationType.UPDATE, content = "撤销预约调整申请")
    @PostMapping("/cancel")
    public Result<Void> cancel(@RequestBody @Valid IdDTO dto) {
        reservationChangeService.cancel(dto.getId());
        return Result.ok();
    }
}
