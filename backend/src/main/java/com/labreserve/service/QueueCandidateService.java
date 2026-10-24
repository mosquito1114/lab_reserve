package com.labreserve.service;

import com.labreserve.model.entity.ResourceQueue;

import java.time.LocalDate;

/**
 * 候补触发服务：**所有「时段被释放」的地方都调它**。
 *
 * <p>三个触发时机（{@code plan.md} §8.1）：
 * <ol>
 *   <li>管理员释放时段（{@code /reservation/release}）</li>
 *   <li>预约取消 / 调整审批通过释放原时段</li>
 *   <li>队首候补超时或放弃（顺延给下一位）</li>
 * </ol>
 *
 * <p>幂等性由「同一 queue 只存在一条 {@code action=0} 的通知记录」保证：
 * 重复触发时看到队首已经是 {@code NOTIFIED}，直接返回。
 */
public interface QueueCandidateService {

    /**
     * 尝试把队首顺延给下一位（时段必须已经空闲）。
     */
    void triggerCandidate(Integer resourceType, Long resourceId, LocalDate reserveDate, Long slotId);

    /**
     * 扫描超时的候补通知：写 7 天禁入 → 置 TIMEOUT → 出队 → 顺延下一位。
     *
     * @return 本轮处理的条数
     */
    int scanTimeout();

    /**
     * 给用户写一条禁入记录（候补超时 / 主动放弃时调用）。
     */
    void ban(Long userId, ResourceQueue queue, String reason);

    /** 当前是否处于禁入期（预检与入队都会用到） */
    boolean isBanned(Long userId, Integer resourceType, Long labId, Long equipmentId);
}
