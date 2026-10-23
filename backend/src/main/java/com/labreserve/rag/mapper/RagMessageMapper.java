package com.labreserve.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labreserve.rag.model.entity.RagMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * RAG 会话消息 Mapper。
 *
 * <p>继承 {@link BaseMapper}；自定义方法聚焦"取最近 N 轮对话 / 反馈更新"。
 */
@Mapper
public interface RagMessageMapper extends BaseMapper<RagMessage> {

    /**
     * 取某会话最近 N 条消息（{@code created_at} 倒序，limit N）。
     *
     * <p>ContextLoader 用它装"最近 6 轮"上下文（12 条）。注意 SQL 是 DESC，
     * 调用方拿到后需再 reverse 成时间正序喂给 LLM。
     */
    @Select("SELECT * FROM tb_rag_message WHERE session_id = #{sessionId} " +
            "ORDER BY id DESC LIMIT #{limit}")
    List<RagMessage> listRecent(@Param("sessionId") Long sessionId, @Param("limit") int limit);

    /** 记录用户反馈（1 有用 / 0 没用） */
    @Update("UPDATE tb_rag_message SET feedback = #{feedback} WHERE id = #{id}")
    int updateFeedback(@Param("id") Long id, @Param("feedback") Integer feedback);
}