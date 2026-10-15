package com.labreserve.rag.pipeline.chunk;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

/**
 * 切片后未持久化的内存数据。
 *
 * <p>写库时由 {@link com.labreserve.rag.mapper.RagChunkMapper} 转成
 * {@code tb_rag_chunk} 行；向量化时再用 {@link #content()} 取原文。
 */
@Getter
@ToString
@AllArgsConstructor
public class RawChunk {

    /** 切片正文 */
    private final String content;

    /** 在原文中起始偏移（含） */
    private final int charStart;

    /** 在原文中终止偏移（不含） */
    private final int charEnd;

    public static RawChunk of(String content, int start, int end) {
        return new RawChunk(content, start, end);
    }
}
