package com.labreserve.infra.oss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.labreserve.common.exception.BizException;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 阿里云 OSS 操作封装。
 *
 * <p>用于故障截图上传（需求模块 4）。上传走<b>后端中转</b>：
 * 前端 multipart → 后端校验 → 本类 putObject → 返回可访问 URL。
 *
 * <p>对象 Key 规范：{@code {dirPrefix}/{bizType}/{yyyy}/{MM}/{uuid}.{ext}}
 * 例如 {@code lab-reserve/REPAIR/2025/09/8f3a2b1c.jpg}
 *
 * <p>OSS 客户端是线程安全的，这里做懒加载单例，并在容器关闭时释放。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AliyunOssTemplate {

    private final OssProperties properties;

    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM");

    private volatile OSS ossClient;

    // ------------------------------------------------------------
    // 客户端
    // ------------------------------------------------------------

    private OSS client() {
        if (ossClient == null) {
            synchronized (this) {
                if (ossClient == null) {
                    if (!properties.isConfigured()) {
                        throw BizException.badRequest("对象存储未配置，请在 .env 中填写 OSS_* 环境变量");
                    }
                    ossClient = new OSSClientBuilder().build(
                            properties.getEndpoint(),
                            properties.getAccessKeyId(),
                            properties.getAccessKeySecret());
                    log.info("[OSS] 客户端初始化完成，endpoint={}, bucket={}",
                            properties.getEndpoint(), properties.getBucket());
                }
            }
        }
        return ossClient;
    }

    @PreDestroy
    public void shutdown() {
        if (ossClient != null) {
            try {
                ossClient.shutdown();
                log.info("[OSS] 客户端已关闭");
            } catch (Exception e) {
                log.warn("[OSS] 关闭客户端失败", e);
            }
        }
    }

    // ------------------------------------------------------------
    // 上传 / 删除
    // ------------------------------------------------------------

    /**
     * 上传并返回可公网访问的 URL。
     *
     * @param inputStream 文件流（调用方负责关闭）
     * @param objectKey   对象 Key，由 {@link #buildObjectKey} 生成
     * @param contentType MIME 类型，如 image/jpeg
     * @param size        文件字节数，可为 null
     */
    public String upload(InputStream inputStream, String objectKey, String contentType, Long size) {
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            if (StringUtils.hasText(contentType)) {
                metadata.setContentType(contentType);
            }
            if (size != null && size > 0) {
                metadata.setContentLength(size);
            }
            PutObjectRequest request = new PutObjectRequest(
                    properties.getBucket(), objectKey, inputStream, metadata);
            client().putObject(request);
            String url = properties.buildUrl(objectKey);
            log.info("[OSS] 上传成功，objectKey={}, url={}", objectKey, url);
            return url;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[OSS] 上传失败，objectKey={}", objectKey, e);
            throw new BizException(500, "图片上传失败，请稍后重试");
        }
    }

    /**
     * 删除对象（删除失败只记日志，不抛异常）。
     */
    public void delete(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return;
        }
        try {
            client().deleteObject(properties.getBucket(), objectKey);
            log.info("[OSS] 删除成功，objectKey={}", objectKey);
        } catch (Exception e) {
            log.warn("[OSS] 删除失败，objectKey={}", objectKey, e);
        }
    }

    // ------------------------------------------------------------
    // Key 生成
    // ------------------------------------------------------------

    /**
     * 生成对象 Key。
     *
     * @param bizType 业务类型，如 REPAIR / AVATAR
     * @param originalFilename 原始文件名，用于提取扩展名
     */
    public String buildObjectKey(String bizType, String originalFilename) {
        LocalDate today = LocalDate.now();
        String ext = extractExtension(originalFilename);
        return "%s/%s/%s/%s/%s%s".formatted(
                trimSlashes(properties.getDirPrefix()),
                StringUtils.hasText(bizType) ? bizType.toUpperCase() : "COMMON",
                YEAR.format(today),
                MONTH.format(today),
                UUID.randomUUID().toString().replace("-", ""),
                ext);
    }

    private static String extractExtension(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot).toLowerCase();
    }

    private static String trimSlashes(String value) {
        if (!StringUtils.hasText(value)) {
            return "lab-reserve";
        }
        String result = value;
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result.isEmpty() ? "lab-reserve" : result;
    }
}
