package com.labreserve.rag.model.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LlmUsage} 单元测试。
 *
 * <p>Token 统计的入口：provider 返回的 usage 从这里转成统一的计数。
 * 测不到 provider 的话，至少要把"解析 + 降级 + 累加"这三段逻辑钉死。
 */
class LlmUsageTest {

    // ============================================================
    // 从 ChatResponse 取 usage
    // ============================================================

    @Test
    @DisplayName("从带 usage 的 ChatResponse 取到真实 token")
    void shouldReadUsageFromChatResponse() {
        ChatResponse resp = new ChatResponse(
                List.of(new Generation(new AssistantMessage("你好"))),
                ChatResponseMetadata.builder()
                        .usage(new DefaultUsage(120, 45, 165))
                        .build());
        LlmUsage usage = LlmUsage.from(resp);
        assertEquals(120, usage.promptTokens());
        assertEquals(45, usage.completionTokens());
        assertEquals(165, usage.totalTokens());
    }

    @Test
    @DisplayName("无 usage 的 ChatResponse → EMPTY（不抛异常）")
    void shouldReturnEmptyWhenNoUsage() {
        ChatResponse resp = new ChatResponse(List.of(new Generation(new AssistantMessage("你好"))));
        assertEquals(LlmUsage.EMPTY, LlmUsage.from(resp));
        assertEquals(0, LlmUsage.from((ChatResponse) null).totalTokens());
    }

    @Test
    @DisplayName("metadata 为 null → EMPTY")
    void shouldReturnEmptyWhenMetadataNull() {
        assertEquals(LlmUsage.EMPTY, LlmUsage.from((ChatResponseMetadata) null));
    }

    @Test
    @DisplayName("usage 有 prompt/completion 但无 total → total = prompt + completion")
    void shouldDeriveTotalWhenMissing() {
        ChatResponse resp = new ChatResponse(List.of(),
                ChatResponseMetadata.builder()
                        .usage(new DefaultUsage(10, 5))
                        .build());
        LlmUsage usage = LlmUsage.from(resp);
        assertEquals(15, usage.totalTokens());
    }

    // ============================================================
    // 累加
    // ============================================================

    @Test
    @DisplayName("plus：多次 LLM 调用的用量合并")
    void shouldSumUsages() {
        LlmUsage a = new LlmUsage(100, 20, 120);
        LlmUsage b = new LlmUsage(50, 10, 60);
        LlmUsage sum = a.plus(b);
        assertEquals(150, sum.promptTokens());
        assertEquals(30, sum.completionTokens());
        assertEquals(180, sum.totalTokens());
    }

    @Test
    @DisplayName("plus(null) → 返回自身")
    void shouldHandleNullInPlus() {
        LlmUsage a = new LlmUsage(10, 5, 15);
        assertEquals(a, a.plus(null));
    }

    // ============================================================
    // 负数保护
    // ============================================================

    @Test
    @DisplayName("负数 token 被归零（防御 provider 返回脏数据）")
    void shouldClampNegativeValues() {
        LlmUsage u = new LlmUsage(-1, -5, -100);
        assertEquals(0, u.promptTokens());
        assertEquals(0, u.completionTokens());
        assertEquals(0, u.totalTokens());
    }

    // ============================================================
    // 本地估算（降级口径）
    // ============================================================

    @Test
    @DisplayName("估算：中文 1 字 ≈ 1 token")
    void shouldEstimateChineseByCharacter() {
        assertEquals(10, LlmUsage.estimate("这是十个中文字符啊哦"));
    }

    @Test
    @DisplayName("估算：ASCII 4 字符 ≈ 1 token")
    void shouldEstimateAsciiByFourChars() {
        assertEquals(2, LlmUsage.estimate("abcdefgh"));
        assertEquals(0, LlmUsage.estimate(""));
        assertEquals(0, LlmUsage.estimate(null));
    }

    @Test
    @DisplayName("estimated() 只算 prompt 侧")
    void shouldBuildPromptOnlyEstimate() {
        // "四个字符" 是 4 个汉字 → 4 token
        LlmUsage u = LlmUsage.estimated("四个字符");
        assertEquals(4, u.promptTokens());
        assertEquals(0, u.completionTokens());
        assertEquals(4, u.totalTokens());
    }

    @Test
    @DisplayName("估算值一定大于 0（provider 不返回 usage 时统计不会恒为 0）")
    void estimateShouldNeverBeZeroForNonEmptyText() {
        assertTrue(LlmUsage.estimate("任意非空文本") > 0);
    }
}
