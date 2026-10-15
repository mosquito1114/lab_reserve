package com.labreserve.rag.pipeline.chunk;

import java.util.List;

/**
 * 切片策略接口（可插拔）。
 *
 * <p>每个实现都是一个独立的 {@code @Component},由 {@link ChunkingStrategyRegistry}
 * 自动发现并按 {@link #code()} 索引。管理员在上传文档时可自由选用；
 * {@link com.labreserve.rag.pipeline.ChunkStep} 通过 Registry 拿到对应实现,
 * <b>不直接引用具体实现类</b>,从而保证新增策略零侵入。
 *
 * <h3>内置策略</h3>
 * <ul>
 *   <li>{@code RECURSIVE} —— 多级分隔符优先级递归(段落 → 行 → 句子 → 词),默认</li>
 *   <li>{@code QA} —— 识别 Q/A / 问题 / 答案 模式,每对 Q+A 一片</li>
 *   <li>{@code FIXED_OVERLAP} —— 纯固定窗 + overlap,扫描 PDF 兜底</li>
 * </ul>
 *
 * <h3>接口位预留(暂未实现)</h3>
 * <ul>
 *   <li>{@code SEMANTIC} —— 句级 embedding 算骤降边界</li>
 *   <li>{@code COMPOSITE} —— 区域级多策略组合</li>
 *   <li>{@code PARENT_CHILD} —— 父子双层粒度</li>
 * </ul>
 *
 * @see SplitContext
 * @see ChunkingStrategyRegistry
 * @see com.labreserve.rag.pipeline.ChunkStep
 */
public interface ChunkingStrategy {

    /** 唯一编码，与 {@code tb_rag_document.chunking_strategy} 字段值一致 */
    String code();

    /** 中文名（前端展示） */
    String name();

    /** 描述（前端 tooltip） */
    String description();

    /** 推荐场景 category 列表（前端 Upload UI 自动高亮推荐） */
    List<String> recommendedFor();

    /** 策略自带默认切长上限；文档级 / 全局配置可覆写 */
    default int defaultMaxChars() {
        return 500;
    }

    /** 策略自带默认重叠（QA 等不需要 overlap 的策略可返回 0） */
    default int defaultOverlap() {
        return 100;
    }

    /** 实际切片 */
    List<RawChunk> split(String cleanText, SplitContext ctx);
}
