package com.labreserve.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labreserve.rag.model.entity.RagSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * RAG 会话 Mapper。
 *
 * <p>继承 {@link BaseMapper} 拿到 CRUD；自定义方法聚焦"最近会话列表 / 计数自增 / 时间戳 touch"。
 */
@Mapper
public interface RagSessionMapper extends BaseMapper<RagSession> {

    /**
     * 取某用户的最近 N 个会话（按 {@code last_message_at} 倒序）。
     *
     * <p>用作侧边栏"我的会话列表"。
     */
    @Select("SELECT * FROM tb_rag_session WHERE user_id = #{userId} AND status = 1 " +
            "ORDER BY last_message_at DESC LIMIT #{limit}")
    List<RagSession> listRecentByUser(@Param("userId") Long userId, @Param("limit") int limit);

    /**
     * 软删除会话（status 置 0）。
     *
     * <p>消息不删，保留用于审计与用量统计。
     * 用显式 SQL 而不是 updateById：后者会跳过 null 字段，
     * 且这里只想动 status 与时间戳，不希望带上其它字段。
     */
    @Update("UPDATE tb_rag_session SET status = 0, updated_at = NOW() " +
            "WHERE id = #{sessionId} AND user_id = #{userId} AND status = 1")
    int softDelete(@Param("sessionId") Long sessionId, @Param("userId") Long userId);

    /** 消息数 +1，并刷新 {@code last_message_at}（每次落消息都调） */
    @Update("UPDATE tb_rag_session SET message_count = message_count + 1, " +
            "last_message_at = NOW(), updated_at = NOW() WHERE id = #{sessionId}")
    int bumpMessage(@Param("sessionId") Long sessionId);

    /** 回填会话标题（首条 USER 消息落库后调） */
    @Update("UPDATE tb_rag_session SET title = #{title}, updated_at = NOW() WHERE id = #{sessionId}")
    int updateTitle(@Param("sessionId") Long sessionId, @Param("title") String title);

    /** 累加最近窗口 token（R5 摘要压缩阈值统计用；R3 只累加） */
    @Update("UPDATE tb_rag_session SET last_token_window = #{tokens}, updated_at = NOW() WHERE id = #{sessionId}")
    int updateTokenWindow(@Param("sessionId") Long sessionId, @Param("tokens") int tokens);
}