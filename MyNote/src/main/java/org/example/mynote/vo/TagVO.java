package org.example.mynote.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 标签视图对象。
 *
 * <p>noteCount 为引用该标签的笔记数，只在 /api/tag/list 场景填充；
 * 标签作为 NoteVO.tags 元素复用时不查询笔记数（保持 null，序列化自动省略）。</p>
 *
 * @author MyNote
 */
@Data
public class TagVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 标签 id
     */
    private Long id;

    /**
     * 标签名
     */
    private String name;

    /**
     * 颜色值
     */
    private String color;

    /**
     * 关联笔记数
     */
    private Integer noteCount;
}
