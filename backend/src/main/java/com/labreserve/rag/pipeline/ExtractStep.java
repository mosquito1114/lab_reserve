package com.labreserve.rag.pipeline;

import com.labreserve.common.exception.BizException;
import com.labreserve.rag.adapter.TikaAdapter;
import com.labreserve.rag.enums.RagPipelineState;
import com.labreserve.rag.enums.StepCode;
import com.labreserve.rag.model.entity.RagDocument;
import com.labreserve.util.AliyunOssTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * EXTRACT Step（design.md §3.3）：OSS → Tika → 写 rawText / charCount。
 *
 * <h3>状态机</h3>
 * <pre>
 *   CREATED → EXTRACTING → EXTRACTED
 * </pre>
 *
 * <h3>流程</h3>
 * <ol>
 *   <li>从 {@code tb_rag_document} 取记录</li>
 *   <li>通过 {@link AliyunOssTemplate#openStream(String)} 拿到原始输入流</li>
 *   <li>{@link TikaAdapter#parse(InputStream, String)} 解析</li>
 *   <li>将解析后的文本 / 字符数写回 {@code raw_text / char_count}</li>
 *   <li>返回统计给 {@link AbstractPipelineStep} 落 {@code output_meta}</li>
 * </ol>
 *
 * <h3>失败语义</h3>
 * OSS 读不到 / Tika 抛异常 → BizException → 基类写 FAILED + last_error_step=EXTRACT。
 *
 * @see TikaAdapter
 * @see AbstractPipelineStep
 */
@Slf4j
@Component
public class ExtractStep extends AbstractPipelineStep {

    @Autowired
    private TikaAdapter tikaAdapter;

    @Autowired
    private AliyunOssTemplate oss;

    @Override
    public StepCode code() {
        return StepCode.EXTRACT;
    }

    @Override
    protected String fromState() {
        return RagPipelineState.CREATED.name();
    }

    @Override
    protected String inState() {
        return RagPipelineState.EXTRACTING.name();
    }

    @Override
    protected String successState() {
        return RagPipelineState.EXTRACTED.name();
    }

    @Override
    protected StepResult apply(StepContext ctx) {
        RagDocument doc = getDocument(ctx.documentId());
        if (doc == null) {
            throw BizException.notFound("文档不存在: " + ctx.documentId());
        }
        String fileUrl = doc.getFileUrl();
        BizException.assertTrue(fileUrl != null && !fileUrl.isBlank(), "文档 file_url 为空");

        log.info("[RAG][EXTRACT] 开始解析 docId={} fileUrl={}", doc.getId(), fileUrl);

        try (InputStream in = oss.openStream(fileUrl)) {
            // Tika 用 source_type 做嗅探补充；filename 可取原文件名的"扩展名.原类型"
            // 这里用 source_type 已经是 "PDF/DOCX/..." 的 V2 约束值，Tika 仍能正确嗅探
            TikaAdapter.Parsed parsed = tikaAdapter.parse(in, doc.getSourceType());

            doc.setRawText(parsed.text());
            doc.setCharCount(parsed.charCount());
            docMapper.updateById(doc);

            return StepResult.ok()
                    .put("pageCount", parsed.pageCount())
                    .put("contentType", parsed.contentType())
                    .put("charCount", parsed.charCount());
        } catch (BizException e) {
            // OSS / Tika 内部已经分类好; 直接抛给基类转 FAILED
            throw e;
        } catch (Exception e) {
            // Tika 故障或未知异常包成 BizException, 让基类统一处理
            log.error("[RAG][EXTRACT] Tika 解析异常 docId={}", doc.getId(), e);
            throw new BizException(500, "文档解析失败: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
