package com.labreserve.rag.util;

import com.labreserve.common.enums.SensitiveType;
import com.labreserve.util.SensitiveUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 流式后置脱敏（v3 安全第③道墙，见 rag-design §12.7）。
 *
 * <h3>为什么需要它</h3>
 * 入库侧 {@code SensitiveScanFilter} 已经把知识库里的手机号/身份证替换成占位符，
 * 但 LLM 仍可能<b>自己生成</b> PII（例如从上下文里拼出号码、或模型幻觉编造）。
 * 本类在流式输出口做最后一道兜底。
 *
 * <h3>难点：跨 chunk 切断</h3>
 * LLM 的 SSE chunk 边界是任意的，一个手机号可能被切成
 * {@code "1381234"} + {@code "5678"}。逐 chunk 做正则替换会漏掉这种被切开的情况。
 *
 * <p>解法：<b>尾部保留（tail hold）</b>——每次只把"不可能再构成手机号前缀"的部分吐出去，
 * 把尾部不完整的数字串留在缓冲区，等下一个 chunk 拼接后再判断：
 * <pre>
 *   chunk1 "电话 1381234"  → 吐出 "电话 "，保留 "1381234"
 *   chunk2 "5678 请拨打"   → 拼接后 "13812345678 请拨打" → 脱敏后吐出 "138****5678 请拨打"
 * </pre>
 *
 * <h3>脱敏口径</h3>
 * 与 {@code SensitiveUtils.maskInText} 一致：只处理<b>大陆手机号</b>。
 * 身份证 18 位不在此处理（正常不会由 LLM 生成；入库侧已清掉），
 * 座机号（如 010-8888-6666）属单位公开信息，<b>有意不脱敏</b>。
 */
@Slf4j
@Component
public class StreamingSanitizer {

    /** 大陆手机号长度（11 位），也是尾部需要保留的最大数字长度 */
    private static final int PHONE_LEN = 11;

    @Autowired
    private SensitiveUtils sensitiveUtils;

    /**
     * 对正文流做脱敏。
     *
     * @param upstream LLM 原始增量流
     * @return 脱敏后的增量流（内容可能因尾部保留而略微延迟）
     */
    public Flux<String> sanitize(Flux<String> upstream) {
        // 每个订阅独立持有缓冲区（用 defer 保证并发请求之间不串数据）
        return Flux.defer(() -> {
            StringBuilder pending = new StringBuilder();
            return upstream
                    .concatMap(chunk -> {
                        pending.append(chunk == null ? "" : chunk);
                        int hold = unsafeSuffixLen(pending);
                        int emitLen = pending.length() - hold;
                        if (emitLen <= 0) {
                            return Flux.empty();
                        }
                        String emit = sensitiveUtils.maskInText(pending.substring(0, emitLen));
                        String rest = pending.substring(emitLen);
                        pending.setLength(0);
                        pending.append(rest);
                        return emit.isEmpty() ? Flux.<String>empty() : Flux.just(emit);
                    })
                    .concatWith(Flux.defer(() -> {
                        // 流结束：把尾部残留吐出去（此时已不可能再有后续字符拼接）
                        if (pending.length() == 0) {
                            return Flux.empty();
                        }
                        String tail = sensitiveUtils.maskInText(pending.toString());
                        pending.setLength(0);
                        return tail.isEmpty() ? Flux.<String>empty() : Flux.just(tail);
                    }));
        });
    }

    /**
     * 计算尾部需要保留的字符数。
     *
     * <p>规则：如果文本以数字结尾且末尾数字串长度 1..10（不足 11 位手机号），
     * 这段数字可能是被切开的手机号前缀，必须保留到下一 chunk 再判断。
     * 长度 ≥ 11 说明已是完整（或更长）数字串，可以安全吐出去（会被脱敏）。
     *
     * @return 0 表示无需保留（可全部吐出）
     */
    private static int unsafeSuffixLen(CharSequence s) {
        int n = s.length();
        int i = n;
        while (i > 0 && Character.isDigit(s.charAt(i - 1))) {
            i--;
        }
        int digits = n - i;
        if (digits == 0) {
            return 0;
        }
        // 不足 11 位 → 可能是手机号前缀，保留
        if (digits < PHONE_LEN) {
            return digits;
        }
        // 完整（或更长）数字串 → 吐出，交给 maskInText 处理
        return 0;
    }

    /**
     * 对单段完整文本做脱敏（非流式场景的便捷入口）。
     */
    public String sanitizeText(String text) {
        return sensitiveUtils.maskInText(text);
    }

    /** 供单测/调试：本类处理的敏感类型说明 */
    public SensitiveType[] handledTypes() {
        return new SensitiveType[]{SensitiveType.PHONE};
    }
}