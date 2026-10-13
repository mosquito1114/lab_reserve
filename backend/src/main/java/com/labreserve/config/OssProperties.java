package com.labreserve.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 阿里云 OSS 配置。
 *
 * <p>所有值都来自环境变量（{@code .env} → docker compose → 容器环境），
 * {@code application.yml} 里只写 {@code ${...}} 占位符。
 * <b>凭据绝不出现在任何提交到 Git 的文件里。</b>
 */
@Data
@Component
@ConfigurationProperties(prefix = "aliyun.oss")
public class OssProperties {

    /** 形如 oss-cn-hangzhou.aliyuncs.com */
    private String endpoint;

    /** Bucket 名称 */
    private String bucket;

    private String accessKeyId;

    private String accessKeySecret;

    /** 自定义域名（CDN 加速域名或绑定的自有域名），为空则使用默认 OSS 域名 */
    private String customDomain;

    /** 对象 Key 的目录前缀，例如 lab-reserve */
    private String dirPrefix = "lab-reserve";

    /** 单文件大小上限（字节），默认 5MB */
    private long maxSize = 5 * 1024 * 1024L;

    /** 是否已配置齐全（未配置时上传接口应明确报错而不是抛 NPE） */
    public boolean isConfigured() {
        return StringUtils.hasText(endpoint)
                && StringUtils.hasText(bucket)
                && StringUtils.hasText(accessKeyId)
                && StringUtils.hasText(accessKeySecret);
    }

    /** 拼接可公网访问的 URL；配了自定义域名就用它 */
    public String buildUrl(String objectKey) {
        if (StringUtils.hasText(customDomain)) {
            String domain = customDomain.endsWith("/")
                    ? customDomain.substring(0, customDomain.length() - 1)
                    : customDomain;
            return domain + "/" + objectKey;
        }
        return "https://" + bucket + "." + endpoint + "/" + objectKey;
    }
}
