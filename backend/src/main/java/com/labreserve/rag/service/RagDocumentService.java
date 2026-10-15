package com.labreserve.rag.service;

import com.labreserve.rag.enums.RagPipelineState;
import com.labreserve.rag.enums.StepCode;
import com.labreserve.rag.model.dto.DocumentUploadDTO;
import com.labreserve.rag.model.dto.ReingestDTO;
import com.labreserve.rag.model.entity.RagDocument;
import com.labreserve.rag.pipeline.StepContext;
import org.springframework.web.multipart.MultipartFile;

/**
 * RAG 文档服务。
 *
 * <p>R1 暴露：
 * <ul>
 *   <li>{@link #uploadAndDispatch(MultipartFile, DocumentUploadDTO)} —— 上传 + sha 幂等 + 入 document 表 + 派发</li>
 *   <li>{@link #getDocument(Long)} —— 详情（管理员）</li>
 *   <li>{@link #reingest(Long, ReingestDTO)} —— 版本号 +1，全链重跑</li>
 *   <li>{@link #runSingleStep(Long, StepCode, String)} —— 手动重跑某 Step</li>
 *   <li>{@link #setBusinessStatus(Long, int)} —— 启停（status 维度）</li>
 * </ul>
 */
public interface RagDocumentService {

    /**
     * 上传文档并异步派发摄取链。
     *
     * <p>幂等：同 sha 已存在则返回已有 id（不会覆盖 title / category 等元数据，
     * 由管理员手动决定是否 reingest）。
     *
     * @return documentId（新建或已存在）
     */
    Long uploadAndDispatch(MultipartFile file, DocumentUploadDTO form);

    /** 取文档详情 */
    RagDocument getDocument(Long id);

    /**
     * 重建索引（{@code POST /reingest}）：把 ingest_version +1，全链重跑。
     *
     * <p>可选传 {@code fileUrl} / 切片参数，三层回退会照旧生效。
     */
    void reingest(Long documentId, ReingestDTO dto);

    /**
     * 手动跑单个 Step（{@code POST /steps/{code}/run}）。
     *
     * <p>会把 doc.pipepline_state 重置到该 Step 的 fromState()，再调 Step.execute()。
     *
     * @param remark 透传到日志（可空）
     */
    void runSingleStep(Long documentId, StepCode code, String remark);

    /** 设置业务状态（0 停用 / 1 启用） */
    void setBusinessStatus(Long documentId, int status);

    /** 把流水线某状态置为另一个状态（admin disable/enable 时常用，但不影响 pipeline_state） */
    void pipelineTransit(Long documentId, RagPipelineState target);
}
