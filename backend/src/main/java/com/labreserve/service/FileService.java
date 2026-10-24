package com.labreserve.service;

import com.labreserve.model.vo.FileUploadVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传（阿里云 OSS 后端中转）。
 *
 * <p>本项目唯一的多文件接口：故障截图。校验分三层——大小、后缀、**文件头魔数**，
 * 后两者一起用才能挡住「改后缀的 txt」。
 */
public interface FileService {

    /**
     * @param file    上传的文件
     * @param bizType 业务类型（OSS 目录分组），为空默认 REPAIR
     */
    FileUploadVO upload(MultipartFile file, String bizType);
}
