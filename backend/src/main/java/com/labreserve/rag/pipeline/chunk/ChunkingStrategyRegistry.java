package com.labreserve.rag.pipeline.chunk;

import com.labreserve.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 切片策略注册中心。
 *
 * <p>启动时把所有 {@code @Component ChunkingStrategy} 按 {@link ChunkingStrategy#code()} 建索引。
 * 后续 {@link com.labreserve.rag.pipeline.ChunkStep} 只通过本类拿策略,
 * <b>不直接 @Autowired 具体策略</b>。
 *
 * <p>新增策略 = 加一个 {@code @Component} 实现,无需改动其他代码。
 *
 * @see ChunkingStrategy
 */
@Slf4j
@Component
public class ChunkingStrategyRegistry {

    private final Map<String, ChunkingStrategy> byCode;

    @Autowired
    public ChunkingStrategyRegistry(List<ChunkingStrategy> strategies) {
        this.byCode = strategies.stream()
                .collect(Collectors.toMap(
                        ChunkingStrategy::code,
                        Function.identity(),
                        (a, b) -> {
                            log.warn("[RAG] 重复的切片策略 code={} 取前者 {}", a.code(), a.getClass().getSimpleName());
                            return a;
                        }));
        // 按 name 排序,确保前端列表稳定
        List<ChunkingStrategy> sorted = new ArrayList<>(byCode.values());
        sorted.sort(Comparator.comparing(ChunkingStrategy::name));
        log.info("[RAG] 已注册 {} 个切片策略: {}", byCode.size(),
                sorted.stream().map(ChunkingStrategy::code).toList());
    }

    /**
     * 按 code 取策略;未知 code 直接抛 400。
     */
    public ChunkingStrategy get(String code) {
        if (code == null || code.isBlank()) {
            throw BizException.badRequest("切片方案 code 不能为空");
        }
        ChunkingStrategy s = byCode.get(code.toUpperCase());
        if (s == null) {
            throw BizException.badRequest("未知切片方案: " + code);
        }
        return s;
    }

    /** 给前端 Upload UI 用 */
    public List<ChunkingStrategy> listAll() {
        return byCode.values().stream()
                .sorted(Comparator.comparing(ChunkingStrategy::name))
                .toList();
    }
}
