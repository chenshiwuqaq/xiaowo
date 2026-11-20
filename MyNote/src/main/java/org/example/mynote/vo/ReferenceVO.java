package org.example.mynote.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 文献条目。
 *
 * @author MyNote
 */
@Data
public class ReferenceVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 文献 id（联网结果为空，尚未入库）
     */
    private Long id;

    /**
     * 标题
     */
    private String title;

    /**
     * 作者
     */
    private String authors;

    /**
     * 期刊/会议
     */
    private String journal;

    /**
     * 年份
     */
    private Integer pubYear;

    /**
     * DOI
     */
    private String doi;

    /**
     * 链接
     */
    private String url;

    /**
     * 学科方向
     */
    private String field;

    /**
     * 摘要或推荐理由
     */
    private String abstractText;

    /**
     * 被引次数
     */
    private Integer citedCount;

    /**
     * 命中的关键词
     */
    private java.util.List<String> matched;

    /**
     * 相关度
     */
    private Integer score;

    /**
     * 推荐理由（模型给出时为挑选理由，否则为规则命中的词）
     */
    private String reason;

    /**
     * 是否已绑定到当前笔记
     */
    private Boolean bound;

    /**
     * 来源：local / online
     */
    private String source;

}
