package com.labreserve.rag.controller;

import com.labreserve.common.annotation.OpLog;
import com.labreserve.common.constant.PermissionConst;
import com.labreserve.common.enums.OperationModule;
import com.labreserve.common.enums.OperationType;
import com.labreserve.common.exception.BizException;
import com.labreserve.common.result.PageResult;
import com.labreserve.common.result.Result;
import com.labreserve.model.dto.IdDTO;
import com.labreserve.rag.config.RagProperties;
import com.labreserve.rag.enums.RagPipelineState;
import com.labreserve.rag.enums.StepCode;
import com.labreserve.rag.mapper.RagChunkMapper;
import com.labreserve.rag.mapper.RagPipelineStepMapper;
import com.labreserve.rag.model.dto.DocumentUploadDTO;
import com.labreserve.rag.model.dto.ReingestDTO;
import com.labreserve.rag.model.dto.StepRunDTO;
import com.labreserve.rag.model.dto.StepRunPathDTO;
import com.labreserve.rag.model.entity.RagDocument;
import com.labreserve.rag.model.entity.RagPipelineStep;
import com.labreserve.rag.model.vo.RagChunkVO;
import com.labreserve.rag.model.vo.RagDocumentVO;
import com.labreserve.rag.model.vo.RagStepTimelineVO;
import com.labreserve.rag.service.RagDocumentService;
import com.labreserve.rag.service.RagIngestDispatcher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import com.labreserve.security.RequirePerm;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * RAG 管理端接口（{@code /api/rag/admin/**}）。
 *
 * <p>全部 {@code POST + JSON body}（上传除外）。对应权限码：
 * <ul>
 *   <li>{@code rag:doc:upload} —— 上传</li>
 *   <li>{@code rag:doc:list}   —— get / steps / chunks / timeline</li>
 *   <li>{@code rag:doc:reindex} —— 重建 / 手动 step run</li>
 *   <li>{@code rag:doc:delete}  —— enable / disable（删除文档保留 R5）</li>
 * </ul>
 *
 * <h3>R1 实装端点</h3>
 * <ol>
 *   <li>{@code POST /upload}            —— multipart 上传 + 派发</li>
 *   <li>{@code POST /get}                —— 文档详情</li>
 *   <li>{@code POST /list_steps}         —— 步骤日志（按 version）</li>
 *   <li>{@code POST /timeline}           —— 步骤 DAG（按 time order）</li>
 *   <li>{@code POST /list_chunks}        —— 切片明细</li>
 *   <li>{@code POST /run_step}           —— 手动跑某 Step</li>
 *   <li>{@code POST /reingest}           —— 重建索引</li>
 *   <li>{@code POST /disable}            —— 停用</li>
 *   <li>{@code POST /enable}             —— 启用</li>
 * </ol>
 *
 * <p>R2 起增 {@code POST /start} 与 {@code POST /list_documents}（分页列表）；R5 加删除硬 / 异步清理。
 */
@Slf4j
@RestController
@RequestMapping("/api/rag/admin")
@Tag(name = "98-RAG-Admin")
public class RagAdminController {

    @Autowired
    private RagDocumentService ragDocumentService;

    @Autowired
    private RagIngestDispatcher dispatcher;

    @Autowired
    private RagChunkMapper chunkMapper;

    @Autowired
    private RagPipelineStepMapper stepMapper;

    @Autowired
    private RagProperties props;

    // --------------------------------------------------------------
    // 1. 上传
    // --------------------------------------------------------------

    @Operation(summary = "上传知识库文档（multipart）",
            description = "异步派发摄取链 (R1 截止到 CHUNK)；同 sha 已存在返回旧 ID 而不重传")
    @RequirePerm(PermissionConst.RAG_DOC_UPLOAD)
    @OpLog(module = OperationModule.RAG, type = OperationType.UPLOAD,
            content = "上传 RAG 文档")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Long> upload(
            @Parameter(description = "知识库文档（PDF/DOCX/MD/TXT/PPT/XLSX）")
            @RequestPart("file") MultipartFile file,
            @ModelAttribute @Valid DocumentUploadDTO form) {
        Long docId = ragDocumentService.uploadAndDispatch(file, form);
        return Result.ok(docId);
    }

    // --------------------------------------------------------------
    // 2. 详情
    // --------------------------------------------------------------

    @Operation(summary = "文档详情")
    @RequirePerm(PermissionConst.RAG_DOC_LIST)
    @PostMapping("/get")
    public Result<RagDocumentVO> get(@RequestBody @Valid IdDTO dto) {
        RagDocumentVO vo = toDocumentVO(ragDocumentService.getDocument(dto.getId()));
        return Result.ok(vo);
    }

    // --------------------------------------------------------------
    // 3. 步骤日志
    // --------------------------------------------------------------

    @Operation(summary = "某文档全部步骤日志（按 ingestVersion）")
    @RequirePerm(PermissionConst.RAG_DOC_LIST)
    @PostMapping("/list_steps")
    public Result<List<RagStepTimelineVO.StepEntry>> listSteps(@RequestBody @Valid IdDTO dto) {
        RagDocument doc = ragDocumentService.getDocument(dto.getId());
        List<RagPipelineStep> steps = stepMapper.listByDocVersion(
                doc.getId(), doc.getIngestVersion());
        return Result.ok(steps.stream().map(this::toStepEntry).toList());
    }

    // --------------------------------------------------------------
    // 4. 时间线（DAG）
    // --------------------------------------------------------------

    @Operation(summary = "文档时间线（DAG）")
    @RequirePerm(PermissionConst.RAG_DOC_LIST)
    @PostMapping("/timeline")
    public Result<RagStepTimelineVO> timeline(@RequestBody @Valid IdDTO dto) {
        RagDocument doc = ragDocumentService.getDocument(dto.getId());
        List<RagPipelineStep> steps = stepMapper.listByDocVersion(
                doc.getId(), doc.getIngestVersion());
        RagStepTimelineVO vo = new RagStepTimelineVO();
        vo.setDocumentId(doc.getId());
        vo.setIngestVersion(doc.getIngestVersion());
        vo.setPipelineState(doc.getPipelineState());
        vo.setSteps(steps.stream().map(this::toStepEntry).toList());
        return Result.ok(vo);
    }

    // --------------------------------------------------------------
    // 5. 切片明细
    // --------------------------------------------------------------

    @Operation(summary = "文档切片明细（R1 用于核查 CHUNK 结果）")
    @RequirePerm(PermissionConst.RAG_DOC_LIST)
    @PostMapping("/list_chunks")
    public Result<List<RagChunkVO>> listChunks(@RequestBody @Valid IdDTO dto) {
        RagDocument doc = ragDocumentService.getDocument(dto.getId());
        var rows = chunkMapper.listByDocVersion(doc.getId(), doc.getIngestVersion());
        List<RagChunkVO> vos = rows.stream().map(row -> {
            RagChunkVO vo = new RagChunkVO();
            vo.setId(row.getId());
            vo.setDocumentId(row.getDocumentId());
            vo.setIngestVersion(row.getIngestVersion());
            vo.setChunkIndex(row.getChunkIndex());
            vo.setContent(row.getContent());
            vo.setCharStart(row.getCharStart());
            vo.setCharEnd(row.getCharEnd());
            vo.setStrategyCode(row.getStrategyCode());
            vo.setVectorRef(row.getVectorRef());
            vo.setCreatedAt(row.getCreatedAt());
            return vo;
        }).toList();
        return Result.ok(vos);
    }

    // --------------------------------------------------------------
    // 6. 手动跑单步
    // --------------------------------------------------------------

    @Operation(summary = "手动重跑某 Step（失败后重试或全链前补充跑）",
            description = "会把 doc.pipepline_state 重置到该 Step 的 fromState，再调 Step.execute()")
    @RequirePerm(PermissionConst.RAG_DOC_REINDEX)
    @OpLog(module = OperationModule.RAG, type = OperationType.UPDATE,
            content = "手动重跑 Step")
    @PostMapping("/run_step")
    public Result<Void> runStep(@RequestBody @Valid StepRunPathDTO dto) {
        StepCode code;
        try {
            code = StepCode.valueOf(dto.getStepCode());
        } catch (IllegalArgumentException e) {
            throw BizException.badRequest("未知 StepCode: " + dto.getStepCode());
        }
        ragDocumentService.runSingleStep(dto.getDocumentId(), code, dto.getRemark());
        return Result.ok();
    }

    /**
     * 跳过某 Step（R5 实现：只允许 FINALIZE）。
     */
    @Operation(summary = "跳过某 Step（仅 FINALIZE 允许）",
            description = "只有 FINALIZE（纯统计汇总）可跳过，直接落 READY；"
                    + "EXTRACT/SANITIZE/CHUNK/EMBED/UPSERT 跳过后数据不完整，一律拒绝")
    @RequirePerm(PermissionConst.RAG_DOC_REINDEX)
    @OpLog(module = OperationModule.RAG, type = OperationType.UPDATE,
            content = "跳过 Step")
    @PostMapping("/skip_step")
    public Result<Void> skipStep(@RequestBody @Valid StepRunPathDTO dto) {
        ragDocumentService.skipStep(dto.getDocumentId(), dto.getStepCode());
        return Result.ok();
    }

    // --------------------------------------------------------------
    // 7. 重建
    // --------------------------------------------------------------

    @Operation(summary = "重建索引：ingestVersion +1、全链重跑",
            description = "会清空上一版的 intermediate 文本与 chunk_count，并派发 EXTRACT→...→FINALIZE")
    @RequirePerm(PermissionConst.RAG_DOC_REINDEX)
    @OpLog(module = OperationModule.RAG, type = OperationType.UPDATE,
            content = "重建索引")
    @PostMapping("/reingest")
    public Result<Void> reingest(@RequestBody @Valid ReingestDTO dto) {
        ragDocumentService.reingest(dto.getDocumentId(), dto);
        return Result.ok();
    }

    // --------------------------------------------------------------
    // 8. 启停
    // --------------------------------------------------------------

    @Operation(summary = "停用文档（业务维度）")
    @RequirePerm(PermissionConst.RAG_DOC_DELETE)
    @OpLog(module = OperationModule.RAG, type = OperationType.UPDATE,
            content = "停用文档")
    @PostMapping("/disable")
    public Result<Void> disable(@RequestBody @Valid IdDTO dto) {
        ragDocumentService.setBusinessStatus(dto.getId(), 0);
        return Result.ok();
    }

    @Operation(summary = "启用文档（业务维度）")
    @RequirePerm(PermissionConst.RAG_DOC_DELETE)
    @OpLog(module = OperationModule.RAG, type = OperationType.UPDATE,
            content = "启用文档")
    @PostMapping("/enable")
    public Result<Void> enable(@RequestBody @Valid IdDTO dto) {
        ragDocumentService.setBusinessStatus(dto.getId(), 1);
        return Result.ok();
    }

    // --------------------------------------------------------------
    // 转换
    // --------------------------------------------------------------

    private RagDocumentVO toDocumentVO(RagDocument d) {
        RagDocumentVO vo = new RagDocumentVO();
        vo.setId(d.getId());
        vo.setTitle(d.getTitle());
        vo.setCategory(d.getCategory());
        vo.setSourceType(d.getSourceType());
        vo.setFileUrl(d.getFileUrl());
        vo.setCharCount(d.getCharCount());
        vo.setChunkCount(d.getChunkCount());
        vo.setPipelineState(d.getPipelineState());
        vo.setIngestVersion(d.getIngestVersion());
        vo.setContentSha256(d.getContentSha256());
        vo.setLastErrorStep(d.getLastErrorStep());
        vo.setLastErrorMsg(d.getLastErrorMsg());
        vo.setLastErrorAt(d.getLastErrorAt());
        vo.setChunkingStrategy(d.getChunkingStrategy());
        vo.setChunkMaxChars(d.getChunkMaxChars());
        vo.setChunkOverlap(d.getChunkOverlap());
        vo.setStatus(d.getStatus());
        vo.setUploadedBy(d.getUploadedBy());
        vo.setCreatedAt(d.getCreatedAt());
        vo.setUpdatedAt(d.getUpdatedAt());
        return vo;
    }

    private RagStepTimelineVO.StepEntry toStepEntry(RagPipelineStep s) {
        RagStepTimelineVO.StepEntry e = new RagStepTimelineVO.StepEntry();
        e.setId(s.getId());
        e.setStepCode(s.getStepCode());
        e.setStatus(s.getStatus());
        e.setInputSha256(s.getInputSha256());
        e.setOutputSha256(s.getOutputSha256());
        e.setOutputMeta(s.getOutputMeta());
        e.setTraceId(s.getTraceId());
        e.setCostMs(s.getCostMs());
        e.setErrorMsg(s.getErrorMsg());
        e.setStartedAt(s.getStartedAt());
        e.setFinishedAt(s.getFinishedAt());
        return e;
    }
}
