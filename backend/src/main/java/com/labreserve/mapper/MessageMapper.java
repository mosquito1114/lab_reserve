package com.labreserve.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labreserve.model.entity.Message;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消息 Mapper。
 *
 * <p>收件箱分页走 Redis ZSet 取 ID 再批量查这里，不用分页插件；
 * 邮件投递记录页才用 `selectPage`。
 */
@Mapper
public interface MessageMapper extends BaseMapper<Message> {
}
