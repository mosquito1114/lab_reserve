package com.labreserve.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labreserve.model.entity.RepairRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 维修记录 Mapper。
 */
@Mapper
public interface RepairRecordMapper extends BaseMapper<RepairRecord> {
}
