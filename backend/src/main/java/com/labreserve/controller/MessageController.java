package com.labreserve.controller;

import com.labreserve.common.annotation.OpLog;
import com.labreserve.common.constant.PermissionConst;
import com.labreserve.common.enums.OperationModule;
import com.labreserve.common.enums.OperationType;
import com.labreserve.common.result.PageResult;
import com.labreserve.common.result.Result;
import com.labreserve.model.dto.IdDTO;
import com.labreserve.model.dto.MessageDeliveryLogPageDTO;
import com.labreserve.model.dto.MessagePageDTO;
import com.labreserve.model.dto.SendSystemMessageDTO;
import com.labreserve.model.dto.SendTestEmailDTO;
import com.labreserve.model.vo.MessageDeliveryLogVO;
import com.labreserve.model.vo.MessageVO;
import com.labreserve.model.vo.UnreadCountVO;
import com.labreserve.security.RequirePerm;
import com.labreserve.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消息通知接口（{@code api.md} §8）。
 *
 * <p>收件箱接口都是「操作自己的数据」，只校验登录态 + {@code message:list}，
 * 不校验数据归属以外的东西（{@code MessageService} 内部会拦别人的消息）。
 * 邮件投递记录只有系统管理员能看（{@code message:delivery-log}）。
 */
@RestController
@RequestMapping("/api/message")
@Tag(name = "06-消息通知")
public class MessageController {

    @Autowired
    private MessageService messageService;

    @Operation(summary = "收件箱分页", description = "按 Redis 收件箱索引倒序；支持类型 / 已读 / 时间筛选")
    @RequirePerm(PermissionConst.MESSAGE_LIST)
    @PostMapping("/get_page")
    public Result<PageResult<MessageVO>> getPage(@RequestBody MessagePageDTO dto) {
        return Result.ok(messageService.page(dto));
    }

    @Operation(summary = "消息详情", description = "自动置为已读")
    @RequirePerm(PermissionConst.MESSAGE_LIST)
    @PostMapping("/get_info")
    public Result<MessageVO> getInfo(@RequestBody @Valid IdDTO dto) {
        return Result.ok(messageService.getInfo(dto.getId()));
    }

    @Operation(summary = "未读数", description = "顶栏铃铛角标；Redis 计数缺失时按库重建")
    @RequirePerm(PermissionConst.MESSAGE_LIST)
    @PostMapping("/get_unread_count")
    public Result<UnreadCountVO> getUnreadCount() {
        return Result.ok(new UnreadCountVO(messageService.unreadCount()));
    }

    @Operation(summary = "单条已读")
    @RequirePerm(PermissionConst.MESSAGE_LIST)
    @PostMapping("/read")
    public Result<Void> read(@RequestBody @Valid IdDTO dto) {
        messageService.read(dto.getId());
        return Result.ok();
    }

    @Operation(summary = "全部已读")
    @RequirePerm(PermissionConst.MESSAGE_LIST)
    @PostMapping("/read_all")
    public Result<Void> readAll() {
        messageService.readAll();
        return Result.ok();
    }

    @Operation(summary = "删除单条", description = "用户视角软删除，同时从收件箱索引移除")
    @RequirePerm(PermissionConst.MESSAGE_LIST)
    @OpLog(module = OperationModule.MESSAGE, type = OperationType.DELETE, content = "删除消息")
    @PostMapping("/delete")
    public Result<Void> delete(@RequestBody @Valid IdDTO dto) {
        messageService.delete(dto.getId());
        return Result.ok();
    }

    @Operation(summary = "一键清空", description = "软删除全部消息 + DEL 收件箱索引")
    @RequirePerm(PermissionConst.MESSAGE_LIST)
    @OpLog(module = OperationModule.MESSAGE, type = OperationType.DELETE, content = "清空消息")
    @PostMapping("/clear")
    public Result<Void> clear() {
        messageService.clear();
        return Result.ok();
    }

    @Operation(summary = "邮件投递记录", description = "仅系统管理员；用于排查「站内信到了但邮件没到」")
    @RequirePerm(PermissionConst.MESSAGE_DELIVERY_LOG)
    @PostMapping("/get_delivery_log_page")
    public Result<PageResult<MessageDeliveryLogVO>> getDeliveryLogPage(
            @RequestBody MessageDeliveryLogPageDTO dto) {
        return Result.ok(messageService.deliveryLogPage(dto));
    }

    @Operation(summary = "发送测试邮件", description = "联调用：直接走 SMTP 发一封，不落库")
    @RequirePerm(PermissionConst.MESSAGE_DELIVERY_LOG)
    @OpLog(module = OperationModule.MESSAGE, type = OperationType.OTHER, content = "发送测试邮件")
    @PostMapping("/send_test_email")
    public Result<Void> sendTestEmail(@RequestBody @Valid SendTestEmailDTO dto) {
        messageService.sendTestEmail(dto.getTo());
        return Result.ok();
    }

    @Operation(summary = "发送系统通知", description = "管理员给指定用户发通知（站内信 + 邮件），返回成功条数")
    @RequirePerm(PermissionConst.MESSAGE_SEND)
    @OpLog(module = OperationModule.MESSAGE, type = OperationType.CREATE, content = "发送系统通知")
    @PostMapping("/send")
    public Result<Integer> send(@RequestBody @Valid SendSystemMessageDTO dto) {
        return Result.ok(messageService.sendSystem(dto.getReceiverIds(), dto.getTitle(), dto.getContent()));
    }
}
