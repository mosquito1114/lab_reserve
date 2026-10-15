package com.labreserve.rag.pipeline.chunk;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * 切片上下文。
 *
 * <p>由 {@link com.labreserve.rag.pipeline.ChunkStep} 在调用策略前组装,
 * 参数顺序为 <b>三层回退</b>（文档级 → 策略自带 → 全局默认）。
 *
 * @see ChunkingStrategy#split(String, SplitContext)
 */
@Getter
@ToString
@RequiredArgsConstructor
public class SplitContext {

    /** 切长上限（已解析） */
    private final int maxChars;

    /** 切窗重叠（已解析，QA 类策略可忽略） */
    private final int overlap;

    /** 当前文档 {@code ingest_version} */
    private final int version;

    /** 当前文档 ID（写库与日志用） */
    private final Long documentId;

    /** 当前文档 category（REGULATION/SOP/MANUAL/FAQ…）,策略可参考 */
    private final String category;
}
