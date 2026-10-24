package com.labreserve.controller;

import com.labreserve.common.annotation.OpLog;
import com.labreserve.common.constant.PermissionConst;
import com.labreserve.common.enums.OperationModule;
import com.labreserve.common.enums.OperationType;
import com.labreserve.common.result.PageResult;
import com.labreserve.common.result.Result;
import com.labreserve.model.dto.IdDTO;
import com.labreserve.model.dto.QueueAbandonDTO;
import com.labreserve.model.dto.QueueCreateDTO;
import com.labreserve.model.dto.QueuePageDTO;
import com.labreserve.model.dto.QueuePositionDTO;
import com.labreserve.model.vo.QueueConfirmVO;
import com.labreserve.model.vo.QueueNoticeVO;
import com.labreserve.model.vo.QueuePositionVO;
import com.labreserve.model.vo.QueueVO;
import com.labreserve.security.RequirePerm;
import com.labreserve.service.QueueService;
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
 * 排队与候补接口（{@code api.md} §6.1）。
 */
@RestController
@RequestMapping("/api/queue")
@Tag(name = "08-排队候补")
public class QueueController {

    @Autowired
    private QueueService queueService;

    @Operation(summary = "加入排队", description = "ZADD NX 幂等 + 锁；有位置时拒绝（应直接预约）")
    @RequirePerm(PermissionConst.QUEUE_CREATE)
    @OpLog(module = OperationModule.QUEUE, type = OperationType.CREATE, content = "加入排队")
    @PostMapping("/create")
    public Result<QueueVO> create(@RequestBody @Valid QueueCreateDTO dto) {
        return Result.ok(queueService.create(dto));
    }

    @Operation(summary = "我的排队", description = "每条都带实时顺位（ZRANK+1）")
    @RequirePerm(PermissionConst.QUEUE_CREATE)
    @PostMapping("/get_my_page")
    public Result<PageResult<QueueVO>> getMyPage(@RequestBody QueuePageDTO dto) {
        return Result.ok(queueService.myPage(dto));
    }

    @Operation(summary = "某资源的完整队列", description = "管理员按 资源+日期+时段 查看排队顺序")
    @RequirePerm(PermissionConst.QUEUE_LIST_ALL)
    @PostMapping("/get_page")
    public Result<PageResult<QueueVO>> getPage(@RequestBody QueuePageDTO dto) {
        return Result.ok(queueService.page(dto));
    }

    @Operation(summary = "排队详情")
    @RequirePerm(PermissionConst.QUEUE_CREATE)
    @PostMapping("/get_info")
    public Result<QueueVO> getInfo(@RequestBody @Valid IdDTO dto) {
        return Result.ok(queueService.getInfo(dto.getId()));
    }

    @Operation(summary = "我的实时顺位", description = "轻量接口，前端轮询用")
    @RequirePerm(PermissionConst.QUEUE_CREATE)
    @PostMapping("/get_my_position")
    public Result<QueuePositionVO> getMyPosition(@RequestBody @Valid QueuePositionDTO dto) {
        return Result.ok(queueService.myPosition(dto));
    }

    @Operation(summary = "取消排队", description = "仅排队中可取消；不写禁入")
    @RequirePerm(PermissionConst.QUEUE_CANCEL)
    @OpLog(module = OperationModule.QUEUE, type = OperationType.UPDATE, content = "取消排队")
    @PostMapping("/cancel")
    public Result<Void> cancel(@RequestBody @Valid IdDTO dto) {
        queueService.cancel(dto.getId());
        return Result.ok();
    }

    @Operation(summary = "候补确认", description = "直接生成 APPROVED 预约单（from_queue=1）")
    @RequirePerm(PermissionConst.QUEUE_CONFIRM)
    @OpLog(module = OperationModule.QUEUE, type = OperationType.UPDATE, content = "候补确认")
    @PostMapping("/confirm")
    public Result<QueueConfirmVO> confirm(@RequestBody @Valid IdDTO dto) {
        return Result.ok(queueService.confirm(dto.getId()));
    }

    @Operation(summary = "放弃候补", description = "写 7 天禁入并顺延下一位")
    @RequirePerm(PermissionConst.QUEUE_CONFIRM)
    @OpLog(module = OperationModule.QUEUE, type = OperationType.UPDATE, content = "放弃候补")
    @PostMapping("/abandon")
    public Result<Void> abandon(@RequestBody @Valid QueueAbandonDTO dto) {
        queueService.abandon(dto.getId(), dto.getReason());
        return Result.ok();
    }

    @Operation(summary = "候补通知历史")
    @RequirePerm(PermissionConst.QUEUE_CREATE)
    @PostMapping("/get_notice_list")
    public Result<List<QueueNoticeVO>> getNoticeList(@RequestBody @Valid IdDTO dto) {
        return Result.ok(queueService.noticeList(dto.getId()));
    }
}
