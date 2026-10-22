package com.labreserve.service;

import com.labreserve.common.result.PageResult;
import com.labreserve.model.dto.AvailabilityQueryDTO;
import com.labreserve.model.dto.EquipmentOptionQueryDTO;
import com.labreserve.model.dto.EquipmentPageDTO;
import com.labreserve.model.dto.EquipmentSaveDTO;
import com.labreserve.model.dto.EquipmentStatusDTO;
import com.labreserve.model.vo.EquipmentAvailabilityVO;
import com.labreserve.model.vo.EquipmentOptionVO;
import com.labreserve.model.vo.EquipmentVO;

import java.util.List;

/**
 * 设备服务。
 *
 * <p>设备是库存制资源：可用量 = 库存 − Σ占用中的预约数量。
 */
public interface EquipmentService {

    PageResult<EquipmentVO> page(EquipmentPageDTO dto);

    EquipmentVO getInfo(Long id);

    Long create(EquipmentSaveDTO dto);

    void update(EquipmentSaveDTO dto);

    /** 状态变更：正常 / 维修中 / 报废 / 停用 */
    void updateStatus(EquipmentStatusDTO dto);

    List<EquipmentOptionVO> listOption(EquipmentOptionQueryDTO dto);

    /** 某日各时段剩余库存（预约页用） */
    EquipmentAvailabilityVO availability(AvailabilityQueryDTO dto);
}
