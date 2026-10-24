package com.labreserve.controller;

import com.labreserve.common.annotation.OpLog;
import com.labreserve.common.enums.OperationModule;
import com.labreserve.common.enums.OperationType;
import com.labreserve.common.result.Result;
import com.labreserve.model.vo.FileUploadVO;
import com.labreserve.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传（{@code api.md} §7.1）——本项目唯一的多文件（multipart）接口。
 *
 * <p>其余接口一律 POST + JSON body，只有这里因为要传二进制而例外。
 */
@RestController
@RequestMapping("/api/file")
@Tag(name = "09-设备运维")
public class FileController {

    @Autowired
    private FileService fileService;

    @Operation(summary = "上传图片", description = "登录即可；≤5MB，jpg/jpeg/png/webp，服务端校验文件头魔数")
    @OpLog(module = OperationModule.REPAIR, type = OperationType.UPLOAD, content = "上传故障截图")
    @PostMapping("/upload")
    public Result<FileUploadVO> upload(@RequestParam("file") MultipartFile file,
                                       @RequestParam(value = "bizType", required = false) String bizType) {
        return Result.ok(fileService.upload(file, bizType));
    }
}
