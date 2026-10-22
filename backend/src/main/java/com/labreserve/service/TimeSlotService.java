package com.labreserve.service;

import com.labreserve.model.dto.DictQueryDTO;
import com.labreserve.model.dto.TimeSlotSaveDTO;
import com.labreserve.model.vo.TimeSlotVO;

import java.util.List;

/**
 * 固定时段字典服务。
 */
public interface TimeSlotService {

    /** 列表（支持按状态过滤），按 sort 升序 */
    List<TimeSlotVO> list(DictQueryDTO dto);

    Long create(TimeSlotSaveDTO dto);

    void update(TimeSlotSaveDTO dto);

    /** 删除（被预约引用时 409） */
    void delete(Long id);
}
