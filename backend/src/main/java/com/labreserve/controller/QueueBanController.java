package com.labreserve.controller;

import com.labreserve.common.result.Result;
import com.labreserve.model.dto.QueueBanQueryDTO;
import com.labreserve.model.vo.QueueBanVO;
import com.labreserve.service.QueueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 排队禁入记录（{@code api.md} §6.2）。仅「我的」视角，登录即可。
 */
@RestController
@RequestMapping("/api/queue_ban")
@Tag(name = "08-排队候补")
public class QueueBanController {

    @Autowired
    private QueueService queueService;

    @Operation(summary = "我的禁入记录", description = "activeOnly=true 只看生效中的")
    @PostMapping("/get_my_list")
    public Result<List<QueueBanVO>> getMyList(@RequestBody(required = false) QueueBanQueryDTO dto) {
        return Result.ok(queueService.myBanList(dto == null ? null : dto.getActiveOnly()));
    }
}
