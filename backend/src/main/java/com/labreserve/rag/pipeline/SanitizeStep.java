package com.labreserve.rag.pipeline;

import com.labreserve.common.exception.BizException;
import com.labreserve.rag.adapter.SensitiveScanFilter;
import com.labreserve.rag.enums.RagPipelineState;
import com.labreserve.rag.enums.StepCode;
import com.labreserve.rag.model.entity.RagDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * SANITIZE Step（design.md §3.3 / §三）：EXTRACT 产物过 {@link SensitiveScanFilter} → 写 cleanText。
 *
 * <h3>状态机</h3>
 * <pre>
 *   EXTRACTED → SANITIZING → SANITIZED
 * </pre>
 *
 * <h3>流程</h3>
 * <ol>
 *   <li>读 {@code tb_rag_document.raw_text}（EXTRACT 留下的原文）</li>
 *   <li>{@link SensitiveScanFilter#scan(String)} 替换三种敏感模式</li>
 *   <li>写 {@code clean_text}；counts 入 {@code output_meta}</li>
 * </ol>
 *
 * <h3>失败语义</h3>
 * raw_text 为空（EXTRACT 实际没产出） → BizException → FAILED。
 *
 * @see SensitiveScanFilter
 */
@Slf4j
@Component
public class SanitizeStep extends AbstractPipelineStep {

    @Autowired
    private SensitiveScanFilter sensitiveScanFilter;

    @Override
    public StepCode code() {
        return StepCode.SANITIZE;
    }

    @Override
    protected String fromState() {
        return RagPipelineState.EXTRACTED.name();
    }

    @Override
    protected String inState() {
        return RagPipelineState.SANITIZING.name();
    }

    @Override
    protected String successState() {
        return RagPipelineState.SANITIZED.name();
    }

    @Override
    protected StepResult apply(StepContext ctx) {
        RagDocument doc = getDocument(ctx.documentId());
        if (doc == null) {
            throw BizException.notFound("文档不存在: " + ctx.documentId());
        }
        String raw = doc.getRawText();
        if (raw == null || raw.isBlank()) {
            throw new BizException(500, "EXTRACT 步骤未产出 raw_text（可能是 EXTRACT 失败或尚未运行）");
        }

        SensitiveScanFilter.SanitizeResult result = sensitiveScanFilter.scan(raw);

        doc.setCleanText(result.cleanedText());
        docMapper.updateById(doc);

        return StepResult.ok()
                .put("id_card", result.counts().getOrDefault("id_card", 0))
                .put("phone", result.counts().getOrDefault("phone", 0))
                .put("email", result.counts().getOrDefault("email", 0))
                .put("cleanCharCount", result.cleanedText() == null ? 0 : result.cleanedText().length());
    }
}
