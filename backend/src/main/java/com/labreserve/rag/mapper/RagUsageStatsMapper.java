package com.labreserve.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labreserve.rag.model.entity.RagUsageStats;
import org.apache.ibatis.annotations.Mapper;

/**
 * RAG 使用统计 Mapper（运营报表 / 成本核算）。
 *
 * <p>R3 起由 {@code RagChatServiceImpl} 每次问答写一行。
 */
@Mapper
public interface RagUsageStatsMapper extends BaseMapper<RagUsageStats> {
}