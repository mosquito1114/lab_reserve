package com.labreserve.service.impl;

import com.labreserve.common.exception.BizException;
import com.labreserve.config.OssProperties;
import com.labreserve.model.vo.FileUploadVO;
import com.labreserve.service.FileService;
import com.labreserve.util.AliyunOssTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/**
 * 文件上传实现。
 *
 * <p>校验顺序：非空 → 大小 ≤ 5MB → 后缀白名单 → Content-Type 以 image/ 开头 → 文件头魔数。
 * 魔数是最关键的一层：`.jpg` 改名的 txt 在前三关都可能通过，但前 8 字节对不上。
 */
@Slf4j
@Service
public class FileServiceImpl implements FileService {

    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "webp");

    @Autowired
    private AliyunOssTemplate ossTemplate;

    @Autowired
    private OssProperties ossProperties;

    @Override
    public FileUploadVO upload(MultipartFile file, String bizType) {
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("请选择要上传的文件");
        }
        long maxSize = ossProperties.getMaxSize() > 0 ? ossProperties.getMaxSize() : 5 * 1024 * 1024L;
        if (file.getSize() > maxSize) {
            throw BizException.badRequest("单张图片不能超过 " + (maxSize / 1024 / 1024) + "MB");
        }
        String original = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "image";
        String ext = original.contains(".")
                ? original.substring(original.lastIndexOf('.') + 1).toLowerCase()
                : "";
        if (!ALLOWED_EXT.contains(ext)) {
            throw BizException.badRequest("仅支持 jpg / jpeg / png / webp 图片");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            throw BizException.badRequest("文件类型必须是图片");
        }
        if (!matchesMagic(file)) {
            throw BizException.badRequest("文件内容非法（不是有效的图片文件）");
        }

        String type = StringUtils.hasText(bizType) ? bizType.trim().toUpperCase() : "REPAIR";
        String objectKey = ossTemplate.buildObjectKey(type, original);
        try (InputStream in = file.getInputStream()) {
            String url = ossTemplate.upload(in, objectKey, contentType, file.getSize());
            FileUploadVO vo = new FileUploadVO();
            vo.setUrl(url);
            vo.setObjectKey(objectKey);
            vo.setName(original);
            vo.setSize(file.getSize());
            vo.setContentType(contentType);
            log.info("[文件] 上传成功 name={}, size={}, url={}", original, file.getSize(), url);
            return vo;
        } catch (BizException e) {
            throw e;
        } catch (IOException e) {
            log.error("[文件] 读取上传流失败", e);
            throw BizException.badRequest("文件读取失败，请重试");
        }
    }

    /** 前 8 字节魔数：JPEG FF D8 FF / PNG 89 50 4E 47 / WEBP "RIFF....WEBP" */
    private boolean matchesMagic(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            byte[] head = in.readNBytes(12);
            if (head.length < 4) {
                return false;
            }
            boolean jpeg = (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF;
            boolean png = (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G';
            boolean webp = head.length >= 12
                    && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                    && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P';
            return jpeg || png || webp;
        } catch (IOException e) {
            return false;
        }
    }
}
