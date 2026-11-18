package com.labreserve.rag.util;

import com.labreserve.util.SensitiveUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link StreamingSanitizer} 单元测试。
 *
 * <p><b>这是 R5 的第③道墙（流式后置脱敏）</b>，也是最容易被忽略的一道：
 * 只对落库那份做脱敏是不够的 —— 用户实时看到的必须是脱敏后的文本。
 *
 * <p>难点在于<b>手机号被 chunk 切断</b>：LLM 可能先吐 "1381234"，下一块才吐 "5678"。
 * 如果不保留尾部等待，客户端会先收到未脱敏的 "1381234"。
 * {@code unsafeSuffixLen} 就是干这个的：尾部不足 11 位的数字串先扣住不发。
 */
class StreamingSanitizerTest {

    private StreamingSanitizer sanitizer;

    @BeforeEach
    void setUp() {
        // SensitiveUtils 是 @Component 且无外部依赖，直接 new 即可；
        // 用 ReflectionTestUtils 注入 private 字段（模拟 Spring 字段注入）
        sanitizer = new StreamingSanitizer();
        ReflectionTestUtils.setField(sanitizer, "sensitiveUtils", new SensitiveUtils());
    }

    // ============================================================
    // 基础
    // ============================================================

    @Test
    @DisplayName("流内完整手机号 → 脱敏输出")
    void shouldMaskCompletePhoneInSingleChunk() {
        String out = collect(Flux.just("联系 13812345678 即可"));
        assertFalse(out.contains("13812345678"), "完整手机号不应出现: " + out);
        assertTrue(out.contains("138****5678"), "应输出脱敏形式: " + out);
    }

    @Test
    @DisplayName("手机号被 chunk 切断 → 先扣住尾部，拼接后再脱敏")
    void shouldMaskPhoneSplitAcrossChunks() {
        String out = collect(Flux.just("联系 1381234", "5678 即可"));
        assertFalse(out.contains("13812345678"), "拼接后的完整手机号不应出现: " + out);
        assertTrue(out.contains("138****5678"), "应输出脱敏形式: " + out);
    }

    @Test
    @DisplayName("手机号被切成 3 块也能正确脱敏")
    void shouldMaskPhoneSplitIntoThreeChunks() {
        String out = collect(Flux.just("电话", "138", "12345678", "结束"));
        assertFalse(out.contains("13812345678"), "不应出现完整手机号: " + out);
        assertTrue(out.contains("138****5678"), "应输出脱敏形式: " + out);
    }

    @Test
    @DisplayName("普通文本原样透传")
    void shouldPassThroughNormalText() {
        String out = collect(Flux.just("离心机使用前要检查", "电源与转头"));
        assertTrue(out.contains("离心机使用前要检查"));
        assertTrue(out.contains("电源与转头"));
    }

    @Test
    @DisplayName("不含敏感信息的短数字不会被误脱敏")
    void shouldNotMaskOrdinaryNumbers() {
        String out = collect(Flux.just("转速设置为 12000 转/分"));
        assertTrue(out.contains("12000"), "普通数字不应被脱敏: " + out);
    }

    @Test
    @DisplayName("空流 / 空 chunk 不炸")
    void shouldHandleEmptyStream() {
        assertEquals("", collect(Flux.empty()));
        assertEquals("", collect(Flux.just("", "")));
    }

    @Test
    @DisplayName("多次订阅互不串数据（defer 保证缓冲区独立）")
    void shouldIsolateConcurrentSubscriptions() {
        // defer 保证每次订阅有独立缓冲区：同一个 upstream 订阅两次，结果应一致且各自完整
        Flux<String> upstream = Flux.just("联系 13812345678 即可");
        String a = collect(upstream);
        String b = collect(upstream);
        assertEquals(a, b);
        assertFalse(a.contains("13812345678"));
        assertTrue(a.contains("138****5678"));
    }

    @Test
    @DisplayName("sanitizeText 单段入口与非流式结果一致")
    void sanitizeTextShouldMaskPhone() {
        String out = sanitizer.sanitizeText("联系 13812345678");
        assertFalse(out.contains("13812345678"));
        assertTrue(out.contains("138****5678"));
    }

    // ============================================================
    // 辅助
    // ============================================================

    /** 走一遍脱敏流并拼成完整字符串 */
    private String collect(Flux<String> upstream) {
        List<String> parts = sanitizer.sanitize(upstream)
                .collectList()
                .block(java.time.Duration.ofSeconds(10));
        return parts == null ? "" : String.join("", parts);
    }
}
