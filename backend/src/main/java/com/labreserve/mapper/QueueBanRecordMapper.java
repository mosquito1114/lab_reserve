package com.labreserve.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labreserve.model.entity.QueueBanRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 排队禁入记录 Mapper。
 */
@Mapper
public interface QueueBanRecordMapper extends BaseMapper<QueueBanRecord> {
}
