package com.labreserve.util;

import com.labreserve.common.annotation.Sensitive;
import com.labreserve.common.enums.SensitiveType;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 脱敏工具（IOC 组件，用 {@code @Autowired} 注入使用）。
 *
 * <p>两种用法：
 * <ol>
 *   <li>VO 字段上标 {@link Sensitive}，序列化时自动脱敏（默认安全）</li>
 *   <li>需要手工处理时注入本类，调 {@link #mask(String, SensitiveType)}</li>
 * </ol>
 *
 * <p>另外提供 {@link #maskInText(String)}，用于操作日志的请求参数脱敏
 * （日志里绝不能出现完整手机号）。
 *
 * <h3>为什么还保留 static 版本</h3>
 * {@link SensitiveSerializer} 由 Jackson 自己用无参构造创建（不是 Spring bean），
 * 拿不到注入，只能调 {@link #staticMask(String, SensitiveType)} /
 * {@link #staticMaskInText(String)}。这是本项目唯一保留静态兜底的场景，
 * <b>其他代码一律注入使用</b>。
 */
@Component
public class SensitiveUtils {

    private static final char MASK_CHAR = '*';

    /** 中国大陆手机号 */
    private static final Pattern PHONE_PATTERN = Pattern.compile("1[3-9]\\d{9}");

    // ------------------------------------------------------------
    // 主入口（注入使用）
    // ------------------------------------------------------------

    /**
     * 按类型脱敏。
     *
     * @param value 原始值，为 null / 空白时原样返回
     * @param type  脱敏类型
     */
    public String mask(String value, SensitiveType type) {
        return doMask(value, type);
    }

    /**
     * 对任意文本里的手机号做脱敏，用于操作日志的请求参数。
     *
     * <p>例：{@code {"phone":"13800001234"}} → {@code {"phone":"138****1234"}}
     */
    public String maskInText(String text) {
        return doMaskInText(text);
    }

    // ------------------------------------------------------------
    // 静态兜底（仅供 Jackson 序列化器等无法注入的场景）
    // ------------------------------------------------------------

    /** 静态版 {@link #mask(String, SensitiveType)}，只给无法注入的调用方使用 */
    public static String staticMask(String value, SensitiveType type) {
        return doMask(value, type);
    }

    /** 静态版 {@link #maskInText(String)}，只给无法注入的调用方使用 */
    public static String staticMaskInText(String text) {
        return doMaskInText(text);
    }

    // ------------------------------------------------------------
    // 实现
    // ------------------------------------------------------------

    private static String doMask(String value, SensitiveType type) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        SensitiveType actual = (type == null) ? SensitiveType.DEFAULT : type;
        return switch (actual) {
            case PHONE -> maskPhone(value);
            case EMAIL -> maskEmail(value);
            case NAME -> maskName(value);
            case BANK_CARD, DEFAULT -> maskByKeep(value, actual.getPrefixKeep(), actual.getSuffixKeep());
        };
    }

    private static String doMaskInText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        Matcher matcher = PHONE_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String phone = matcher.group();
            matcher.appendReplacement(sb, phone.substring(0, 3) + "****" + phone.substring(7));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /** 13800001234 → 138****1234 */
    private static String maskPhone(String phone) {
        if (phone.length() < 7) {
            return maskByKeep(phone, 1, 1);
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /** abcdef@qq.com → abc***@qq.com */
    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return maskByKeep(email, 3, 0);
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        String maskedLocal;
        if (local.length() <= 3) {
            maskedLocal = local.charAt(0) + "***";
        } else {
            maskedLocal = local.substring(0, 3) + "***";
        }
        return maskedLocal + domain;
    }

    /** 张三 → 张*，欧阳修 → 欧** */
    private static String maskName(String name) {
        if (name.length() <= 1) {
            return name + MASK_CHAR;
        }
        return name.charAt(0) + String.valueOf(MASK_CHAR).repeat(name.length() - 1);
    }

    /** 保留前 prefix 位与后 suffix 位，中间全部替换成 * */
    private static String maskByKeep(String value, int prefix, int suffix) {
        int len = value.length();
        if (len <= prefix + suffix) {
            // 太短，只保留首位
            return value.charAt(0) + String.valueOf(MASK_CHAR).repeat(Math.max(1, len - 1));
        }
        int maskLen = len - prefix - suffix;
        return value.substring(0, prefix)
                + String.valueOf(MASK_CHAR).repeat(maskLen)
                + (suffix > 0 ? value.substring(len - suffix) : "");
    }
}
