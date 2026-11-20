package org.example.mynote.controller;

import org.example.mynote.common.Result;
import org.example.mynote.service.FileService;
import org.example.mynote.vo.UploadVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传接口。
 *
 * <p>需登录（不在鉴权白名单内）。multipart 字段名固定为 file，与契约一致；
 * type 可省略，缺省按 image 存储，非法 type 会被服务层拒绝——它同时是目录名，必须白名单化。</p>
 *
 * @author MyNote
 */
@RestController
@RequestMapping("/api/file")
public class FileController {

    /**
     * multipart 中的文件字段名，前端 FormData 需与此一致
     */
    private static final String PART_NAME = "file";

    /**
     * 缺省业务类型：正文图片
     */
    private static final String DEFAULT_TYPE = "image";

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    /**
     * 上传图片（头像或正文图），返回可访问的相对路径。
     *
     * <p>体积超限由 Spring 在进入本方法前抛出，已有全局处理器给出中文提示，此处不重复拦截。</p>
     *
     * @param file 图片文件，multipart 字段名 file
     * @param type 业务类型：avatar / image，可选，默认 image
     * @return 访问地址、服务端文件名、字节大小
     */
    @PostMapping("/upload")
    public Result<UploadVO> upload(@RequestParam(PART_NAME) MultipartFile file,
                                   @RequestParam(value = "type", required = false,
                                           defaultValue = DEFAULT_TYPE) String type) {
        return Result.ok(fileService.upload(file, type));
    }
}
