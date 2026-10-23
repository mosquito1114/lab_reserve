package com.labreserve.rag.adapter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档敏感信息扫描器（SANITIZE Step 的具体实现）。
 *
 * <p>本类<b>不只统计，还做脱敏替换</b>：把所有命中的身份证 / 手机 / 邮箱替换成
 * {@code "[REDACTED:xxx]"} 占位符，并把命中数记到 {@link SanitizeResult#counts()}。
 *
 * <p>为什么不用 {@code SensitiveUtils.maskInText}？
 * 后者<b>只覆盖手机号</b>，不识别身份证 / 邮箱。SANITIZE 需要三种覆盖，
 * 因此本类自维护正则，保证扫描行为稳定、易测试。
 *
 * <p>红线（design.md §3 三 / §11.6）：
 * 任何表、日志、导入文件<b>禁止出现身份证号</b>。一旦命中，必须计数并替换，
 * 让管理员能从 {@code SanitizeResult.counts().get("id_card")} 看到有多少
 * 身份证流入，必要时 {@code @OpLog} 上报到操作日志。
 */
@Slf4j
@Component
public class SensitiveScanFilter {

    /**
     * 18 位大陆居民身份证（GB 11643-1999）：6 位地址码 + 8 位生日 + 3 位顺序 + 1 位校验
     * 末位允许 X / x。
     */
    private static final Pattern ID_CARD = Pattern.compile(
            "[1-9]\\d{5}(?:18|19|20)\\d{2}(?:0\\d|1[0-2])(?:[0-2]\\d|3[01])\\d{3}[\\dXx]");

    /** 11 位大陆手机号（与 {@code SensitiveUtils} 内部正则保持一致；不复用以避免依赖私字段） */
    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");

    /** 通用邮箱（不校验 TLD 合法性，只取最宽集合） */
    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    /**
     * 对正文做一次敏感扫描 + 替换。
     *
     * @param text 待扫描的纯文本（可为 null）
     * @return 替换后的文本 + 命中数（counts 三键始终存在，未命中为 0）
     */
    public SanitizeResult scan(String text) {
        if (text == null) {
            return new SanitizeResult(null, counts(0, 0, 0));
        }
        if (text.isEmpty()) {
            return new SanitizeResult("", counts(0, 0, 0));
        }

        // 用可变计数器 + 替换回调，命中时自增，最后回填到 counts
        int[] id = {0};
        int[] phone = {0};
        int[] email = {0};

        String cleaned = replaceAll(text, ID_CARD, m -> { id[0]++; return "[REDACTED:ID_CARD]"; });
        cleaned = replaceAll(cleaned, PHONE, m -> { phone[0]++; return "[REDACTED:PHONE]"; });
        cleaned = replaceAll(cleaned, EMAIL, m -> { email[0]++; return "[REDACTED:EMAIL]"; });

        if (id[0] > 0) {
            log.warn("[RAG] SANITIZE 检测到 {} 条身份证号，已替换为占位符（来源文档含敏感信息，建议人工核验）", id[0]);
        }
        if (phone[0] > 0 || email[0] > 0) {
            log.info("[RAG] SANITIZE 命中统计: phone={}, email={}", phone[0], email[0]);
        }

        return new SanitizeResult(cleaned, counts(id[0], phone[0], email[0]));
    }

    /**
     * 通用正则替换（回调式）。
     *
     * <p>用 {@link Matcher#appendReplacement(StringBuffer, String)} 写追加替换逻辑，
     * 但传入字符串不是字面量而是占位符时仍需要 {@link Matcher#quoteReplacement}
     * 转义（虽然 {@code [REDACTED:xxx]} 没有 $ / \\，保险起见写一下）。
     */
    private static String replaceAll(String input, Pattern pattern,
                                     Function<MatchResult, String> callback) {
        Matcher m = pattern.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String replacement = callback.apply(m);
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static Map<String, Integer> counts(int idCard, int phone, int email) {
        Map<String, Integer> map = new LinkedHashMap<>(4);
        map.put("id_card", idCard);
        map.put("phone", phone);
        map.put("email", email);
        return map;
    }

    /**
     * SANITIZE 结果。
     *
     * @param cleanedText 替换敏感信息后的正文
     * @param counts      命中计数（key 集合固定：id_card / phone / email）
     */
    public record SanitizeResult(String cleanedText, Map<String, Integer> counts) {
    }
}
