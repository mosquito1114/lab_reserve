package com.labreserve.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labreserve.rag.model.entity.RagChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * RAG 切片明细 Mapper。
 *
 * <p>CHUNK Step 写库时按 (documentId, ingestVersion) 物理删除旧 version
 * 然后批量插入新 row;R2 起会同步刷 Qdrant points。
 */
@Mapper
public interface RagChunkMapper extends BaseMapper<RagChunk> {

    /** 按 (docId, version) 取所有切片(用于引用追溯 / 调试) */
    @Select("SELECT * FROM tb_rag_chunk WHERE document_id = #{documentId} " +
            "AND ingest_version = #{ingestVersion} ORDER BY chunk_index ASC")
    List<RagChunk> listByDocVersion(@Param("documentId") Long documentId,
                                    @Param("ingestVersion") Integer ingestVersion);

    /**
     * 物理删除同 doc 同 version 的旧切片。
     * <p>每次 CHUNK Step 重跑前必调,确保旧 version 不残留。
     * 注意 MyBatis-Plus BlockAttackInnerInterceptor 要求必须有 WHERE,这里有。
     */
    @Update("DELETE FROM tb_rag_chunk WHERE document_id=#{documentId} AND ingest_version=#{version}")
    int deleteByDocVersion(@Param("documentId") Long documentId,
                           @Param("version") Integer version);
}
