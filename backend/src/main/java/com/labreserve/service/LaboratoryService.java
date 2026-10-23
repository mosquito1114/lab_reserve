package com.labreserve.service;

import com.labreserve.common.result.PageResult;
import com.labreserve.model.dto.AvailabilityQueryDTO;
import com.labreserve.model.dto.LaboratoryOptionQueryDTO;
import com.labreserve.model.dto.LaboratoryPageDTO;
import com.labreserve.model.dto.LaboratorySaveDTO;
import com.labreserve.model.dto.LaboratoryStatusDTO;
import com.labreserve.model.vo.LaboratoryAvailabilityVO;
import com.labreserve.model.vo.LaboratoryDetailVO;
import com.labreserve.model.vo.LaboratoryOptionVO;
import com.labreserve.model.vo.LaboratoryVO;

import java.util.List;

/**
 * 实验室服务。
 *
 * <p>所有写操作都会写 {@code tb_resource_update_log}（强制更新原因 + before/after 快照）。
 */
public interface LaboratoryService {

    PageResult<LaboratoryVO> page(LaboratoryPageDTO dto);

    /** 详情：含关联设备 */
    LaboratoryDetailVO getInfo(Long id);

    Long create(LaboratorySaveDTO dto);

    void update(LaboratorySaveDTO dto);

    /** 停用 / 启用 */
    void updateStatus(LaboratoryStatusDTO dto);

    /** 下拉（默认只返回可用） */
    List<LaboratoryOptionVO> listOption(LaboratoryOptionQueryDTO dto);

    /**
     * 某日各时段可用情况（预约页用）。
     *
     * <p>判定顺序：停用 → 日期已过 → 非开放星期 → 不在开放时间范围 → 已被预约；
     * 无论是否可用都会返回排队人数与我的顺位。
     */
    LaboratoryAvailabilityVO availability(AvailabilityQueryDTO dto);
}
