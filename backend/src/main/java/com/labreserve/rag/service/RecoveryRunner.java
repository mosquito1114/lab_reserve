package com.labreserve.rag.service;

import com.labreserve.rag.config.RagProperties;
import com.labreserve.rag.enums.RagPipelineState;
import com.labreserve.rag.mapper.RagDocumentMapper;
import com.labreserve.rag.model.entity.RagDocument;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 摄取失败自动续跑（design.md §3.8 / R5）。
 *
 * <h3>做什么</h3>
 * 定时扫描处于 {@code FAILED} 状态的文档，对<b>满足续跑条件</b>的重新派发全链：
 * <ul>
 *   <li>{@code retryCount < ingest.retryAttempts}（默认 3 次）</li>
 *   <li>距上次失败已超过 {@code ingest.retryBackoffMs}（避免疯狂重试）</li>
 * </ul>
 *
 * <h3>为什么用 {@code ingestVersion + 1} 而不是原地重跑</h3>
 * 原地重跑会覆盖上一轮的 {@code tb_rag_chunk} / step 日志，失败现场丢失。
 * 递增版本号让每轮有独立的 chunk / step 记录，便于排查（design §3.3「重跑需 reingest 新版本」）。
 *
 * <h3>幂等与并发</h3>
 * <ul>
 *   <li>Redisson 分布式锁 {@code lab:reserve:rag:recovery}，多实例只有一个跑</li>
 *   <li>锁租约 5 分钟（本次扫描总耗时上限），看门狗自动续期不启用</li>
 * </ul>
 *
 * <h3>开关</h3>
 * {@code lab-reserve.rag.recovery.enabled}（默认 true）。关闭后只保留手动重试。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "lab-reserve.rag.enabled", havingValue = "true")
public class RecoveryRunner {

    /** 每次扫描最多处理的文档数（防止一次拉太多把 Embedding 配额打爆） */
    private static final int BATCH_LIMIT = 5;

    /** 分布式锁 key */
    private static final String LOCK_KEY = "lab:reserve:rag:recovery";

    @Autowired
    private RagDocumentMapper docMapper;

    @Autowired
    private RagIngestDispatcher dispatcher;

    @Autowired
    private RagProperties props;

    @Autowired
    private RedissonClient redisson;

    /**
     * 定时扫描（默认每 5 分钟一次，可通过 {@code recovery.interval-ms} 调）。
     */
    @Scheduled(fixedDelayString = "${lab-reserve.rag.recovery.interval-ms:300000}",
            initialDelayString = "${lab-reserve.rag.recovery.initial-delay-ms:60000}")
    public void recoverFailedDocuments() {
        if (!props.getRecovery().isEnabled()) {
            return;
        }
        RLock lock = redisson.getLock(LOCK_KEY);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(1, 300, TimeUnit.SECONDS);
            if (!acquired) {
                log.debug("[RAG][RECOVERY] 另一实例正在扫描，跳过本轮");
                return;
            }
            doRecover();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[RAG][RECOVERY] 获取锁被中断");
        } catch (Exception e) {
            log.error("[RAG][RECOVERY] 扫描异常", e);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    // ------------------------------------------------------------

    private void doRecover() {
        int retryAttempts = Math.max(1, props.getIngest().getRetryAttempts());
        long backoffMs = Math.max(0L, props.getIngest().getRetryBackoffMs());
        LocalDateTime threshold = LocalDateTime.now().minus(Duration.ofMillis(backoffMs));

        List<RagDocument> failed = docMapper.selectList(
                Wrappers.<RagDocument>lambdaQuery()
                        .eq(RagDocument::getPipelineState, RagPipelineState.FAILED.name())
                        .le(RagDocument::getLastErrorAt, threshold)
                        .orderByAsc(RagDocument::getLastErrorAt)
                        .last("LIMIT " + BATCH_LIMIT));

        if (failed == null || failed.isEmpty()) {
            return;
        }

        log.info("[RAG][RECOVERY] 发现 {} 篇失败文档待重试（上限 {} 次，退避 ≥{}ms）",
                failed.size(), retryAttempts, backoffMs);

        for (RagDocument doc : failed) {
            // ingest_version 当"已重试轮次"用：v1 是首轮，v>retryAttempts 说明已超出上限
            int version = doc.getIngestVersion() == null ? 1 : doc.getIngestVersion();
            if (version >= retryAttempts) {
                log.warn("[RAG][RECOVERY] 文档 {} 已重试 {} 轮（上限 {}），跳过，等人工介入",
                        doc.getId(), version, retryAttempts);
                continue;
            }
            try {
                log.info("[RAG][RECOVERY] 自动续跑 docId={} v{} → v{} (上次失败: {} / {})",
                        doc.getId(), version, version + 1,
                        doc.getLastErrorStep(), abbreviate(doc.getLastErrorMsg()));
                // ★ 必须先重置状态再派发：否则链内每个 transitTo 都会因当前态=FAILED 而失败
                resetForRetry(doc, version + 1);
                dispatcher.runChain(doc.getId(), version + 1);
            } catch (Exception e) {
                log.error("[RAG][RECOVERY] 续跑 docId={} 失败: {}", doc.getId(), e.getMessage());
            }
        }
    }

    /**
     * 续跑前把文档重置成"新一轮待执行"的形态，再派发。
     *
     * <p>与 {@code RagDocumentService.reingest} 的区别：这里只做最小状态重置，
     * 不碰文件与切片参数（失败重试应沿用原参数）。
     */
    private void resetForRetry(RagDocument doc, int newVersion) {
        doc.setIngestVersion(newVersion);
        doc.setPipelineState(RagPipelineState.CREATED.name());
        doc.setLastErrorStep(null);
        doc.setLastErrorMsg(null);
        doc.setLastErrorAt(null);
        doc.setRawText(null);
        doc.setCleanText(null);
        doc.setChunkCount(0);
        docMapper.updateById(doc);
    }

    private static String abbreviate(String s) {
        if (s == null) return "";
        return s.length() <= 80 ? s : s.substring(0, 80) + "…";
    }
}