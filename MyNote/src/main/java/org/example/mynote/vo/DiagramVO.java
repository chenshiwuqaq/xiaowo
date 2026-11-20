package org.example.mynote.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 逻辑图。
 *
 * @author MyNote
 */
@Data
public class DiagramVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 图 id
     */
    private Long id;

    /**
     * 标题
     */
    private String title;

    /**
     * 图类型
     */
    private String diagramType;

    /**
     * 参与笔记 id 串
     */
    private String noteIds;

    /**
     * Mermaid 源码
     */
    private String code;

    /**
     * 生成方式：ai / local
     */
    private String generator;

    /**
     * 创建时间
     */
    private java.time.LocalDateTime createTime;

}
