package com.labreserve.rag.enums;

import com.labreserve.common.exception.BizException;
import lombok.Getter;

/**
 * RAG 知识库文档类别。
 *
 * <p>对应 {@code tb_rag_document.category} 列（VARCHAR(32)）。
 * 类别与切片策略是<b>两个维度</b>：
 * <ul>
 *   <li>category 表达"这篇文档属于什么类型的知识"（REGULATION / MANUAL / FAQ / SOP）</li>
 *   <li>chunking_strategy 表达"用什么方式切"（RECURSIVE / QA / FIXED_OVERLAP）</li>
 *   <li>前端 Upload UI 用 category 推荐默认 chunking_strategy（见 {@link com.labreserve.rag.pipeline.chunk.ChunkingStrategy#recommendedFor()}）</li>
 * </ul>
 *
 * <p>只允许这四类。{@code PDF_SCAN} <b>不是</b>类别，是 {@code FIXED_OVERLAP}
 * 策略的推荐场景，前端按文件扩展名识别后给出提示。
 *
 * @see com.labreserve.rag.model.entity.RagDocument#getCategory()
 */
@Getter
public enum RagDocumentCategory {

    /** 规章制度（如安全手册、实验管理办法） */
    REGULATION("REGULATION", "规章制度"),

    /** 操作手册（如仪器使用指南） */
    MANUAL("MANUAL", "操作手册"),

    /** 常见问题（FAQ） */
    FAQ("FAQ", "常见问题"),

    /** 实验流程（SOP） */
    SOP("SOP", "实验流程"),
    ;

    /** 写入 {@code tb_rag_document.category} 的字符串编码（与 v2 设计图约束一致） */
    private final String code;

    /** 中文名（前端展示） */
    private final String description;

    RagDocumentCategory(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 把字符串规范化为枚举；code 必须等于四类之一（大小写不敏感）。
     *
     * @param code 客户端传入的 category 字符串
     * @return 命中的枚举
     * @throws BizException 400 当输入不在四类之内或为 null/空
     */
    public static RagDocumentCategory requireOf(String code) {
        if (code == null || code.isBlank()) {
            throw BizException.badRequest("文档类别不能为空");
        }
        for (RagDocumentCategory c : values()) {
            if (c.code.equalsIgnoreCase(code.trim())) {
                return c;
            }
        }
        throw BizException.badRequest("未知文档类别: " + code + "（仅支持 REGULATION/MANUAL/FAQ/SOP）");
    }
}
