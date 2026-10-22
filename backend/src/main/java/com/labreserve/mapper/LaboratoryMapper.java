package com.labreserve.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labreserve.model.entity.Laboratory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 实验室 Mapper。
 */
@Mapper
public interface LaboratoryMapper extends BaseMapper<Laboratory> {
}
