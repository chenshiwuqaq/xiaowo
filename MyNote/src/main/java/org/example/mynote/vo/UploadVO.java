package org.example.mynote.vo;

import lombok.Data;

/**
 * 文件上传结果。
 *
 * <p>url 是相对路径（如 /upload/avatar/202610/xxx.png），前端自行拼 baseURL 显示；
 * name 返回的是服务端重命名后的文件名（UUID），不回传客户端原始文件名，
 * 避免任何把用户输入当可信展示内容或路径片段的隐患。</p>
 *
 * @author MyNote
 */
@Data
public class UploadVO {

    /**
     * 访问相对路径，正斜杠分隔
     */
    private String url;

    /**
     * 服务端重命名后的文件名
     */
    private String name;

    /**
     * 文件大小（字节）
     */
    private Long size;
}
