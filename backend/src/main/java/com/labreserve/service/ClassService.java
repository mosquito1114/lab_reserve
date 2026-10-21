package com.labreserve.service;

import com.labreserve.common.result.PageResult;
import com.labreserve.model.dto.ClassListDTO;
import com.labreserve.model.dto.ClassPageDTO;
import com.labreserve.model.dto.ClassSaveDTO;
import com.labreserve.model.vo.ClassVO;

import java.util.List;

/**
 * 班级服务（用户模块的配套字典）。
 *
 * <p>班级是学生学院 / 专业的唯一来源，也是教师排课时选择「上课班级」的依据，
 * 因此它既是管理员维护的数据，也是预约模块的下拉数据源。
 */
public interface ClassService {

    /** 班级下拉（只返回启用状态，支持学院/年级/关键字筛选） */
    List<ClassVO> list(ClassListDTO dto);

    /** 班级分页（管理员维护用，含停用班级） */
    PageResult<ClassVO> page(ClassPageDTO dto);

    /** 新增班级（班级名全局唯一） */
    Long create(ClassSaveDTO dto);

    /** 修改班级（班级名唯一性排除自身） */
    void update(ClassSaveDTO dto);
}
