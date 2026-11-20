package org.example.mynote.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 笔记列表项视图对象。
 *
 * <p>刻意不含正文：列表接口要控制响应体积，前端渲染卡片只需要标题、摘要与元信息。
 * 字段名与 docs/API.md 第 5 节逐字对齐，noteTypeName 由后端翻译，
 * 省掉前端再维护一份"1 科研 2 生活…"的魔法值表。</p>
 *
 * @author MyNote
 */
@Data
public class NoteVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 笔记 id
     */
    private Long id;

    /**
     * 标题
     */
    private String title;

    /**
     * 摘要（服务端保存时截取）
     */
    private String summary;

    /**
     * 归属目录 id，0 为未分类
     */
    private Long folderId;

    /**
     * 类型：1 科研，2 生活，3 小说，4 随笔
     */
    private Integer noteType;

    /**
     * 类型中文名
     */
    private String noteTypeName;

    /**
     * 字数
     */
    private Integer wordCount;

    /**
     * 是否置顶
     */
    private Boolean top;

    /**
     * 标签集合
     */
    private List<TagVO> tags = new ArrayList<>();

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 最后更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
