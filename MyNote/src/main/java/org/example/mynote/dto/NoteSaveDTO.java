package org.example.mynote.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 新建笔记入参。
 *
 * <p>标题允许为空——创作者常先写正文再拟题，服务端会用正文首行推断；
 * 归属目录不传即落"未分类"（folderId = 0）。userId 一律取自登录态，不在入参中出现。</p>
 *
 * @author MyNote
 */
@Data
public class NoteSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 标题，可空；为空时由服务端从正文推断
     */
    @Size(max = 200, message = "标题长度不能超过 200 字")
    private String title;

    /**
     * Markdown 正文
     */
    private String content;

    /**
     * 归属目录 id，0 或不传表示未分类
     */
    @Min(value = 0, message = "目录 id 不能为负数")
    private Long folderId;

    /**
     * 笔记类型：1 科研，2 生活，3 小说，4 随笔；不传默认随笔
     */
    @Min(value = 1, message = "笔记类型取 1-4")
    @Max(value = 4, message = "笔记类型取 1-4")
    private Integer noteType;

    /**
     * 标签名列表，不存在的标签会自动创建
     */
    @Size(max = 20, message = "单篇笔记最多绑定 20 个标签")
    private List<@Size(max = 50, message = "标签名不能超过 50 字") String> tagNames;
}
