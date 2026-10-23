package com.labreserve.service;

import com.labreserve.model.dto.DictQueryDTO;
import com.labreserve.model.dto.ExperimentTypeSaveDTO;
import com.labreserve.model.vo.ExperimentTypeVO;

import java.util.List;

/**
 * 实验类型字典服务。
 *
 * <p>字典被实验室 / 预约引用后不允许删除（只能停用），避免历史数据失去含义。
 */
public interface ExperimentTypeService {

    /** 列表（支持按状态过滤），按 sort 升序 */
    List<ExperimentTypeVO> list(DictQueryDTO dto);

    /** 新增，返回新 ID */
    Long create(ExperimentTypeSaveDTO dto);

    /** 修改 */
    void update(ExperimentTypeSaveDTO dto);

    /** 删除（被引用时 409） */
    void delete(Long id);
}
