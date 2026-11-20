package org.example.mynote.service;

import org.example.mynote.vo.UploadVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传服务：头像与笔记内图片落盘。
 *
 * <p>存储位置与访问前缀都来自配置（mynote.upload.*），实现不感知 Web 层细节，
 * 失败一律 BizException，由全局异常处理器转统一响应。</p>
 *
 * @author MyNote
 */
public interface FileService {

    /**
     * 保存一张上传图片到 type/yyyyMM/ 子目录，文件名重命名为 UUID。
     *
     * @param file 上传的图片文件
     * @param type 业务类型：avatar / image，为空按 image 处理
     * @return 访问相对路径、服务端文件名与文件大小
     */
    UploadVO upload(MultipartFile file, String type);
}
