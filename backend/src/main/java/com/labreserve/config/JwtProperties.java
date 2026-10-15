package com.labreserve.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置（{@code lab-reserve.jwt.*}）。
 *
 * <pre>
 * lab-reserve:
 *   jwt:
 *     secret: ${JWT_SECRET:...}                   # HS256 密钥，至少 32 字节
 *     access-expire-seconds: ${JWT_ACCESS_EXPIRE:7200}
 *     refresh-expire-seconds: ${JWT_REFRESH_EXPIRE:604800}
 * </pre>
 *
 * <p>密钥只从环境变量注入（{@code .env}，已被 .gitignore 忽略），配置文件里只留占位符。
 */
@Data
@Component
@ConfigurationProperties(prefix = "lab-reserve.jwt")
public class JwtProperties {

    /** HS256 签名密钥，长度必须 >= 32 字节 */
    private String secret;

    /** Access Token 有效期（秒），默认 2 小时 */
    private long accessExpireSeconds = 7200L;

    /** Refresh Token 有效期（秒），默认 7 天 */
    private long refreshExpireSeconds = 604800L;
}
