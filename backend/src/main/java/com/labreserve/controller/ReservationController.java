package com.labreserve.controller;

import com.labreserve.common.annotation.OpLog;
import com.labreserve.common.constant.PermissionConst;
import com.labreserve.common.enums.OperationModule;
import com.labreserve.common.enums.OperationType;
import com.labreserve.common.result.PageResult;
import com.labreserve.common.result.Result;
import com.labreserve.model.dto.ReservationAuditDTO;
import com.labreserve.model.dto.ReservationCancelDTO;
import com.labreserve.model.dto.ReservationPageDTO;
import com.labreserve.model.dto.ReservationSubmitDTO;
import com.labreserve.model.dto.ReservationTimetableDTO;
import com.labreserve.model.dto.IdDTO;
import com.labreserve.model.vo.ReservationCheckVO;
import com.labreserve.model.vo.ReservationDetailVO;
import com.labreserve.model.vo.ReservationVO;
import com.labreserve.model.vo.TimetableVO;
import com.labreserve.security.RequirePerm;
import com.labreserve.service.ReservationService;
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
 * 预约接口（{@code api.md} §4）。
 *
 * <p>权限按「实验室 / 设备」分开：实验室只有教师（{@code reservation:lab:create}），
 * 设备的预约权限师生都有（{@code reservation:equipment:create}）。
 * 服务层还会按 {@code resourceType} 再校验一次角色（学生约实验室 → 409）。
 */
@RestController
@RequestMapping("/api/reservation")
@Tag(name = "07-预约管理")
public class ReservationController {

    @Autowired
    private ReservationService reservationService;

    @Operation(summary = "可用性预检", description = "返回可约 / 冲突码 / 排队人数 / 禁入信息，不落库")
    @PostMapping("/check")
    public Result<ReservationCheckVO> check(@RequestBody @Valid ReservationSubmitDTO dto) {
        return Result.ok(reservationService.check(dto));
    }

    @Operation(summary = "提交预约", description = "分布式锁 + 唯一索引双保险；冲突返回 409 与 conflictCode")
    @OpLog(module = OperationModule.RESERVATION, type = OperationType.CREATE, content = "提交预约")
    @PostMapping("/create")
    public Result<ReservationDetailVO> create(@RequestBody @Valid ReservationSubmitDTO dto) {
        return Result.ok(reservationService.create(dto));
    }

    @Operation(summary = "预约分页", description = "管理员按状态/资源/日期/申请人筛选")
    @RequirePerm(PermissionConst.RESERVATION_LIST_ALL)
    @PostMapping("/get_page")
    public Result<PageResult<ReservationVO>> getPage(@RequestBody ReservationPageDTO dto) {
        return Result.ok(reservationService.page(dto));
    }

    @Operation(summary = "我的预约", description = "只看本人，登录即可")
    @PostMapping("/get_my_page")
    public Result<PageResult<ReservationVO>> getMyPage(@RequestBody ReservationPageDTO dto) {
        return Result.ok(reservationService.myPage(dto));
    }

    @Operation(summary = "待审核列表")
    @RequirePerm(PermissionConst.RESERVATION_AUDIT)
    @PostMapping("/get_pending_page")
    public Result<PageResult<ReservationVO>> getPendingPage(@RequestBody ReservationPageDTO dto) {
        return Result.ok(reservationService.pendingPage(dto));
    }

    @Operation(summary = "预约详情", description = "本人或具备 reservation:view")
    @PostMapping("/get_info")
    public Result<ReservationDetailVO> getInfo(@RequestBody @Valid IdDTO dto) {
        return Result.ok(reservationService.getInfo(dto.getId()));
    }

    @Operation(summary = "审核预约", description = "通过时锁内二次判冲；通过/驳回都会通知申请人")
    @RequirePerm(PermissionConst.RESERVATION_AUDIT)
    @OpLog(module = OperationModule.RESERVATION, type = OperationType.AUDIT, content = "审核预约")
    @PostMapping("/audit")
    public Result<Void> audit(@RequestBody @Valid ReservationAuditDTO dto) {
        reservationService.audit(dto);
        return Result.ok();
    }

    @Operation(summary = "撤销预约", description = "仅本人 + 仅待审核")
    @OpLog(module = OperationModule.RESERVATION, type = OperationType.UPDATE, content = "撤销预约")
    @PostMapping("/cancel")
    public Result<Void> cancel(@RequestBody @Valid ReservationCancelDTO dto) {
        reservationService.cancel(dto);
        return Result.ok();
    }

    @Operation(summary = "释放时段", description = "管理员操作；置 CANCELED 并通知申请人（候补顺延在迭代 6）")
    @RequirePerm(PermissionConst.RESERVATION_RELEASE)
    @OpLog(module = OperationModule.RESERVATION, type = OperationType.UPDATE, content = "释放预约时段")
    @PostMapping("/release")
    public Result<Void> release(@RequestBody @Valid ReservationCancelDTO dto) {
        reservationService.release(dto);
        return Result.ok();
    }

    @Operation(summary = "我的实验课表", description = "学生按班级反查已通过的实验课")
    @RequirePerm(PermissionConst.RESERVATION_TIMETABLE)
    @PostMapping("/get_my_timetable")
    public Result<List<TimetableVO>> getMyTimetable(@RequestBody(required = false) ReservationTimetableDTO dto) {
        return Result.ok(reservationService.timetable(dto == null ? new ReservationTimetableDTO() : dto));
    }
}
