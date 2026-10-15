package com.labreserve.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labreserve.rag.model.entity.RagDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * RAG 文档主表 Mapper。
 *
 * <p>继承 {@link BaseMapper} 拿到 CRUD;
 * 自定义方法聚焦"状态机推进 + 失败标记 + sha 幂等"等 RAG 专属 SQL。
 */
@Mapper
public interface RagDocumentMapper extends BaseMapper<RagDocument> {

    /** 按 sha 查已存在文档（幂等保护;同 sha 上传返回已存在 ID） */
    RagDocument findBySha(@Param("sha256") String sha256);

    /**
     * 推进状态机：{@code WHERE id=? AND pipeline_state=fromState}。
     * <p>只在当前态匹配时推进,避免并发跑步骤时把状态机打乱。
     * 返回 0 表示状态不匹配(并发跑 / 手动调过),由调用方决定是否抛错。
     */
    @Update("UPDATE tb_rag_document SET pipeline_state=#{toState}, updated_at=NOW() " +
            "WHERE id=#{id} AND pipeline_state=#{fromState}")
    int transitTo(@Param("id") Long id,
                  @Param("fromState") String fromState,
                  @Param("toState") String toState);

    /** 失败标记:写 last_error_* 同时把状态置 FAILED。 */
    @Update("UPDATE tb_rag_document SET pipeline_state='FAILED', last_error_step=#{errorStep}, " +
            "last_error_msg=#{errorMsg}, last_error_at=NOW(), updated_at=NOW() WHERE id=#{id}")
    int markFailed(@Param("id") Long id,
                   @Param("errorStep") String errorStep,
                   @Param("errorMsg") String errorMsg);

    /** 软启停(业务维度 status=0/1) */
    @Update("UPDATE tb_rag_document SET status=#{status}, updated_at=NOW() WHERE id=#{id}")
    int setBusinessStatus(@Param("id") Long id, @Param("status") Integer status);
}
