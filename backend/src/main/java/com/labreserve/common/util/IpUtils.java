package com.labreserve.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * 客户端 IP 提取工具。
 *
 * <p>操作日志必须记录真实 IP。生产环境前端经过 nginx 反代，
 * 因此要优先读 {@code X-Forwarded-For}。
 *
 * <p>配套要求：{@code application.yml} 里设置
 * {@code server.forward-headers-strategy: framework}，
 * 否则容器内拿到的 remoteAddr 只是 nginx 的地址。
 */
public final class IpUtils {

    private IpUtils() {
    }

    private static final String UNKNOWN = "unknown";

    private static final String[] HEADERS = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP",
            "HTTP_X_FORWARDED_FOR"
    };

    /**
     * 获取客户端 IP。
     *
     * <p>X-Forwarded-For 可能是逗号分隔的链路，取第一个非 unknown 的地址。
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        for (String header : HEADERS) {
            String ip = request.getHeader(header);
            if (isValid(ip)) {
                int comma = ip.indexOf(',');
                String first = (comma > 0) ? ip.substring(0, comma) : ip;
                return first.trim();
            }
        }
        String remote = request.getRemoteAddr();
        if (!StringUtils.hasText(remote)) {
            return UNKNOWN;
        }
        // 本机 IPv6 回环统一显示成 127.0.0.1，便于阅读
        if ("0:0:0:0:0:0:0:1".equals(remote) || "::1".equals(remote)) {
            return "127.0.0.1";
        }
        return remote;
    }

    /**
     * 截断 User-Agent 到数据库列宽以内（tb_operation_log.user_agent 为 VARCHAR(255)）。
     */
    public static String truncateUserAgent(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() > 255 ? userAgent.substring(0, 255) : userAgent;
    }

    private static boolean isValid(String ip) {
        return StringUtils.hasText(ip) && !UNKNOWN.equalsIgnoreCase(ip);
    }
}
