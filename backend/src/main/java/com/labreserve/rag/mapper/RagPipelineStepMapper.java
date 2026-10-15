package com.labreserve.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labreserve.rag.model.entity.RagPipelineStep;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * RAG 步骤日志 Mapper。
 *
 * <p>每跑一步落一行;支持按 (documentId, ingestVersion) 查询、按 traceId 串联。
 * status: 1 RUNNING / 2 SUCCESS / 3 FAILED / 4 SKIPPED。
 */
@Mapper
public interface RagPipelineStepMapper extends BaseMapper<RagPipelineStep> {

    /** 找当前文档某 version 最近 6 步(详情页用) */
    List<RagPipelineStep> listRecentByDocVersion(@Param("documentId") Long documentId,
                                                 @Param("ingestVersion") Integer ingestVersion,
                                                 @Param("limit") int limit);

    /** 找当前文档某 version 全部 step(按时间正序) */
    List<RagPipelineStep> listByDocVersion(@Param("documentId") Long documentId,
                                          @Param("ingestVersion") Integer ingestVersion);

    /**
     * 写 RUNNING 行;MyBatis-Plus 默认会把 PK 写回 {@link RagPipelineStep#id}。
     */
    int insertRunning(RagPipelineStep step);

    @Update("UPDATE tb_rag_pipeline_step SET status=2, output_sha256=#{outputSha256}, " +
            "output_meta=#{outputMeta}, cost_ms=#{costMs}, finished_at=NOW() " +
            "WHERE id=#{id} AND status=1")
    int markSuccess(@Param("id") Long id,
                    @Param("outputSha256") String outputSha256,
                    @Param("outputMeta") String outputMeta,
                    @Param("costMs") Integer costMs);

    @Update("UPDATE tb_rag_pipeline_step SET status=3, error_msg=#{errorMsg}, finished_at=NOW() " +
            "WHERE id=#{id} AND status=1")
    int markFailed(@Param("id") Long id, @Param("errorMsg") String errorMsg);

    @Update("UPDATE tb_rag_pipeline_step SET status=4, finished_at=NOW() WHERE id=#{id}")
    int markSkipped(@Param("id") Long id);
}
