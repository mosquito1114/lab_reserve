package com.labreserve.rag.service.impl;

import cn.hutool.core.io.IoUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.labreserve.common.exception.BizException;
import com.labreserve.rag.config.RagProperties;
import com.labreserve.rag.enums.RagPipelineState;
import com.labreserve.rag.enums.StepCode;
import com.labreserve.rag.mapper.RagDocumentMapper;
import com.labreserve.rag.model.dto.DocumentUploadDTO;
import com.labreserve.rag.model.dto.ReingestDTO;
import com.labreserve.rag.model.entity.RagDocument;
import com.labreserve.rag.pipeline.AbstractPipelineStep;
import com.labreserve.rag.pipeline.ChunkStep;
import com.labreserve.rag.pipeline.EmbedStep;
import com.labreserve.rag.pipeline.ExtractStep;
import com.labreserve.rag.pipeline.FinalizeStep;
import com.labreserve.rag.pipeline.SanitizeStep;
import com.labreserve.rag.pipeline.UpsertStep;
import com.labreserve.rag.pipeline.StepContext;
import com.labreserve.rag.service.RagDocumentService;
import com.labreserve.rag.service.RagIngestDispatcher;
import com.labreserve.util.AliyunOssTemplate;
import com.labreserve.util.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link RagDocumentService} 实现。
 *
 * <p>摄取链路（{@link #uploadAndDispatch} → {@link RagIngestDispatcher#runChain}）
 * 是 R1 的主入口。本类把所有"文档表状态变更 + 文件上传 + sha 幂等 + @Async 派发"
 * 聚合在一处，业务 Controller 直接调它就行。
 *
 * <p>依赖注入全部用 {@code @Autowired} 字段注入（项目约定，见 AGENTS.md §2）。
 */
@Slf4j
@Service
public class RagDocumentServiceImpl implements RagDocumentService {

    /** 允许上传的扩展名（小写，与 {@code tb_rag_document.source_type} 对应） */
    private static final List<String> ALLOWED_EXTS = Arrays.asList(
            "pdf", "docx", "md", "markdown", "txt", "pptx", "xlsx");

    @Autowired
    private RagProperties props;

    @Autowired
    private AliyunOssTemplate oss;

    @Autowired
    private RagDocumentMapper docMapper;

    @Autowired
    private RagIngestDispatcher dispatcher;

    @Autowired
    private com.labreserve.rag.adapter.QdrantAdapter qdrantAdapter;

    @Autowired
    private SecurityUtils securityUtils;

    /**
     * Step Bean 表（按 StepCode 索引），用 {@link Map} 在 {@link #initStepRegistry()} 一次性构造，
     * 避免每次单步重跑去查 {@code ApplicationContext}。
     */
    private final Map<StepCode, AbstractPipelineStep> stepRegistry = new HashMap<>();

    @Autowired
    public RagDocumentServiceImpl(ExtractStep extractStep, SanitizeStep sanitizeStep,
                                 ChunkStep chunkStep, EmbedStep embedStep,
                                 UpsertStep upsertStep, FinalizeStep finalizeStep) {
        stepRegistry.put(StepCode.EXTRACT, extractStep);
        stepRegistry.put(StepCode.SANITIZE, sanitizeStep);
        stepRegistry.put(StepCode.CHUNK, chunkStep);
        stepRegistry.put(StepCode.EMBED, embedStep);
        stepRegistry.put(StepCode.UPSERT, upsertStep);
        stepRegistry.put(StepCode.FINALIZE, finalizeStep);
        log.info("[RAG] 已注册 {} 个 Step Bean: {}", stepRegistry.size(), stepRegistry.keySet());
    }

    // --------------------------------------------------------------
    // 上传
    // --------------------------------------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long uploadAndDispatch(MultipartFile file, DocumentUploadDTO form) {
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("文件为空");
        }
        // 1. 大小 / 扩展名校验（一次性集中校验, 任何一步失败立刻 400）
        int maxMb = props.getIngest().getMaxFileMb();
        BizException.assertTrue(file.getSize() <= maxMb * 1024L * 1024L,
                "文件超过 " + maxMb + " MB 上限");
        String ext = extOf(file.getOriginalFilename()).toLowerCase();
        BizException.assertTrue(ALLOWED_EXTS.contains(ext),
                "仅支持 PDF/DOCX/MD/TXT/PPT/XLSX，不支持: " + ext);

        // 2. sha256（用于幂等 & 上传后比对）
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (java.io.IOException e) {
            throw BizException.badRequest("文件读取失败: " + e.getMessage());
        }
        String sha = DigestUtil.sha256Hex(bytes);

        // 3. 同 sha 已存在直接返回（幂等保护，design.md §3.5 D 类"同 sha 重传"）
        RagDocument existed = docMapper.findBySha(sha);
        if (existed != null) {
            log.info("[RAG] 上传幂等命中: docId={} sha={} (跳过上传与派发)",
                    existed.getId(), sha.substring(0, 12) + "…");
            return existed.getId();
        }

        // 4. OSS 上传（中央路径: lab-reserve/RAG_DOC/yyyy/MM/<uuid>.<ext>）
        String objectKey = oss.buildObjectKey("RAG_DOC", file.getOriginalFilename());
        String url = oss.upload(
                new ByteArrayInputStream(bytes),
                objectKey,
                file.getContentType(),
                file.getSize());

        // 5. 写入 document
        RagDocument doc = new RagDocument();
        doc.setTitle(form.getTitle() == null || form.getTitle().isBlank()
                ? file.getOriginalFilename()
                : form.getTitle());
        doc.setCategory(form.parsedCategory().getCode());
        doc.setSourceType(extOf(file.getOriginalFilename()).toUpperCase());
        doc.setFileUrl(url);
        doc.setContentSha256(sha);
        doc.setStatus(0); // 默认停用，admin enable 后才参与检索
        doc.setPipelineState(RagPipelineState.CREATED.name());
        doc.setIngestVersion(1);
        doc.setUploadedBy(securityUtils.getUserId());
        doc.setChunkingStrategy(form.getChunkingStrategy());
        doc.setChunkMaxChars(form.getChunkMaxChars());
        doc.setChunkOverlap(form.getChunkOverlap());
        docMapper.insert(doc);

        log.info("[RAG] 新建文档 docId={} title='{}' category={} sha={} ossKey={}",
                doc.getId(), doc.getTitle(), doc.getCategory(),
                sha.substring(0, 12) + "…", objectKey);

        // 6. 异步派发摄取链
        dispatcher.runChain(doc.getId(), 1);

        return doc.getId();
    }

    // --------------------------------------------------------------
    // 查询
    // --------------------------------------------------------------

    @Override
    public RagDocument getDocument(Long id) {
        RagDocument doc = docMapper.selectById(id);
        BizException.requireFound(doc, "文档不存在");
        return doc;
    }

    // --------------------------------------------------------------
    // 重建
    // --------------------------------------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reingest(Long documentId, ReingestDTO dto) {
        RagDocument doc = getDocument(documentId);
        doc.setIngestVersion(doc.getIngestVersion() + 1);
        doc.setPipelineState(RagPipelineState.CREATED.name());
        doc.setLastErrorStep(null);
        doc.setLastErrorMsg(null);
        doc.setLastErrorAt(null);
        doc.setRawText(null);
        doc.setCleanText(null);
        doc.setCharCount(0);
        doc.setChunkCount(0);

        if (dto.getFileUrl() != null && !dto.getFileUrl().isBlank()) {
            doc.setFileUrl(dto.getFileUrl());
        }
        if (dto.getChunkingStrategy() != null && !dto.getChunkingStrategy().isBlank()) {
            doc.setChunkingStrategy(dto.getChunkingStrategy());
        }
        if (dto.getChunkMaxChars() != null && dto.getChunkMaxChars() > 0) {
            doc.setChunkMaxChars(dto.getChunkMaxChars());
        }
        if (dto.getChunkOverlap() != null && dto.getChunkOverlap() >= 0) {
            doc.setChunkOverlap(dto.getChunkOverlap());
        }
        docMapper.updateById(doc);

        log.info("[RAG] 重建 docId={} v={} reason={}", doc.getId(), doc.getIngestVersion(), dto.getReason());
        dispatcher.runChain(doc.getId(), doc.getIngestVersion());
    }

    // --------------------------------------------------------------
    // 单步重跑
    // --------------------------------------------------------------

    @Override
    public void runSingleStep(Long documentId, StepCode code, String remark) {
        AbstractPipelineStep step = stepRegistry.get(code);
        if (step == null) {
            throw BizException.badRequest("不支持的 Step: " + code + "（R1 已实装 EXTRACT/SANITIZE/CHUNK; R2 起 EMBED/UPSERT/FINALIZE）");
        }
        RagDocument doc = getDocument(documentId);

        // 把 doc 重置到该 Step 的 fromState()，让基类的 transitTo 能向前推进
        // （注意：FAILED 也能恢复；若当前态不是 fromState，基类会返回 0 并静默标 FAILED）
        String targetFromState = fromStateOf(step);
        int updated = docMapper.transitTo(doc.getId(),
                null /* 不带 fromState 约束，强制重置 */,
                targetFromState);
        if (updated == 0) {
            // transitTo 写死了两个 fromState 不能同时为 null, 这里我们退一步:用 updateById
            doc.setPipelineState(targetFromState);
            doc.setLastErrorStep(null);
            doc.setLastErrorMsg(null);
            doc.setLastErrorAt(null);
            docMapper.updateById(doc);
        } else {
            // 上面更新了 state, 但 last_error_* 字段没清。再 update 一次清错。
            doc.setLastErrorStep(null);
            doc.setLastErrorMsg(null);
            doc.setLastErrorAt(null);
            docMapper.updateById(doc);
        }

        String traceId = dispatcher.newTraceId();
        StepContext ctx = StepContext.of(doc.getId(), doc.getIngestVersion(), traceId);

        log.info("[RAG] 管理员手动单步重跑 docId={} v={} step={} remark={}",
                doc.getId(), doc.getIngestVersion(), code, remark);
        dispatcher.runSingleStep(ctx, step);
    }

    // --------------------------------------------------------------
    // 启停
    // --------------------------------------------------------------

    @Override
    public void setBusinessStatus(Long documentId, int status) {
        BizException.assertTrue(status == 0 || status == 1, "status 仅支持 0/1");
        int affected = docMapper.setBusinessStatus(documentId, status);
        if (affected == 0) {
            throw BizException.notFound("文档不存在");
        }
        // 同步 Qdrant payload.status —— 检索侧按 payload.status=1 过滤，
        // 不同步会导致刚启用的文档在检索时仍被排除（R3 联调发现的缺陷）
        qdrantAdapter.syncDocStatus(documentId, status);
        log.info("[RAG] 文档 {} 业务状态 -> {}", documentId, status == 1 ? "启用" : "停用");
    }

    @Override
    public void pipelineTransit(Long documentId, RagPipelineState target) {
        // 强制覆盖（fromState=null 走不到，掉头直接 updateById）
        RagDocument doc = docMapper.selectById(documentId);
        BizException.requireFound(doc, "文档不存在");
        doc.setPipelineState(target.name());
        docMapper.updateById(doc);
    }

    // --------------------------------------------------------------
    // 私有
    // --------------------------------------------------------------

    /**
     * AbstractPipelineStep 是抽象类（不带 Spring 注解），无法直接取 fromState()；
     * 用一个 switch 把"Step 编码 → 进入该 Step 前要求的状态"映射出来。
     * 与步骤本身的 fromState() 实现保持一致；新增 Step 时这里同步加一个分支。
     */
    private static String fromStateOf(AbstractPipelineStep step) {
        StepCode code = step.code();
        return switch (code) {
            case EXTRACT -> RagPipelineState.CREATED.name();
            case SANITIZE -> RagPipelineState.EXTRACTED.name();
            case CHUNK -> RagPipelineState.SANITIZED.name();
            case EMBED -> RagPipelineState.CHUNKED.name();
            case UPSERT -> RagPipelineState.EMBEDDED.name();
            case FINALIZE -> RagPipelineState.UPSERTED.name();
        };
    }

    private static String extOf(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1);
    }
}
