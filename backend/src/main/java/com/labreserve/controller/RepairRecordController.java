package com.labreserve.controller;

import com.labreserve.common.result.Result;
import com.labreserve.model.dto.IdDTO;
import com.labreserve.model.vo.RepairRecordVO;
import com.labreserve.service.RepairService;
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
 * 维修记录（{@code api.md} §7.3）。
 */
@RestController
@RequestMapping("/api/repair_record")
@Tag(name = "09-设备运维")
public class RepairRecordController {

    @Autowired
    private RepairService repairService;

    @Operation(summary = "某工单的维修记录", description = "按时间升序，详情页时间线数据源")
    @PostMapping("/get_list")
    public Result<List<RepairRecordVO>> getList(@RequestBody @Valid IdDTO orderId) {
        return Result.ok(repairService.recordList(orderId.getId()));
    }
}
