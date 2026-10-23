package com.labreserve.rag.model.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 意图分类结果（Step 3 IntentRouter 输出）。
 *
 * <p>v3 范围收缩后的 4 维（去掉 {@code biz}，新增 {@code out_of_scope}），见 rag-design §12.5。
 * 判定优先级（短路）：{@code unsafe > out_of_scope > chitchat > docs}。
 *
 * <p>本对象既用于 QUERY 链内部传递，也序列化进 {@code tb_rag_message.intent} 供后续统计。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "意图分类结果")
public class Intent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "文档问答意图 0~1")
    private double docs;

    @Schema(description = "闲聊意图 0~1")
    private double chitchat;

    @Schema(description = "越权/注入意图 0~1（≥0.3 直接拒）")
    private double unsafe;

    @Schema(description = "业务操作意图 0~1（≥0.5 引导去业务页面，不检索）")
    private double outOfScope;

    // ============================================================
    // 阈值判定（与 rag-design §12.5 一致）
    // ============================================================

    public static final double T_UNSAFE = 0.3;
    public static final double T_OUT_OF_SCOPE = 0.5;
    public static final double T_CHITCHAT = 0.7;
    public static final double T_DOCS = 0.5;

    public boolean isUnsafe() {
        return unsafe >= T_UNSAFE;
    }

    public boolean isOutOfScope() {
        return outOfScope >= T_OUT_OF_SCOPE;
    }

    public boolean isChitchat() {
        return chitchat >= T_CHITCHAT;
    }

    public boolean useDocs() {
        return docs >= T_DOCS;
    }

    /** 四维都不 decisive → 落到 docs 兜底（宁可去检索也不轻易拒） */
    public boolean isUndetermined() {
        return !isUnsafe() && !isOutOfScope() && !isChitchat() && !useDocs();
    }

    /** 按优先级短路返回最终意图类型（用于 QUERY 链分叉） */
    public Type dominant() {
        if (isUnsafe()) {
            return Type.UNSAFE;
        }
        if (isOutOfScope()) {
            return Type.OUT_OF_SCOPE;
        }
        if (isChitchat()) {
            return Type.CHITCHAT;
        }
        return Type.DOCS;
    }

    public enum Type {
        DOCS, CHITCHAT, UNSAFE, OUT_OF_SCOPE
    }

    // ============================================================
    // 工厂
    // ============================================================

    public static Intent undetermined() {
        Intent i = new Intent();
        i.docs = 0.4;
        return i;
    }

    public static Intent chitchat() {
        Intent i = new Intent();
        i.chitchat = 1.0;
        return i;
    }

    public static Intent unsafe() {
        Intent i = new Intent();
        i.unsafe = 1.0;
        return i;
    }

    public static Intent outOfScope() {
        Intent i = new Intent();
        i.outOfScope = 1.0;
        return i;
    }

    public static Intent docs() {
        Intent i = new Intent();
        i.docs = 1.0;
        return i;
    }
}