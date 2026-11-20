package org.example.mynote.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 背景知识卡片。
 *
 * @author MyNote
 */
@Data
public class KnowledgeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 卡片 id
     */
    private Long id;

    /**
     * 分类
     */
    private String category;

    /**
     * 时代
     */
    private String era;

    /**
     * 地域
     */
    private String region;

    /**
     * 标题
     */
    private String title;

    /**
     * 正文
     */
    private String content;

    /**
     * 命中的关键词
     */
    private java.util.List<String> hitWords;

    /**
     * 出处
     */
    private String source;

    /**
     * 相关度分值
     */
    private Integer score;

}
