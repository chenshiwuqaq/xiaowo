package org.example.mynote.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 创作背景知识卡片数据对象，对应表 mn_knowledge_card。
 *
 * <p>小说/生活类笔记写作时，后端按 keywords 扫描正文命中并浮现对应常识，
 * 例如写到"长安""坊市""宵禁"即给出对应朝代的生活信息。</p>
 *
 * @author MyNote
 */
@Data
@Entity
@Table(name = "mn_knowledge_card")
public class KnowledgeCardDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 历史背景 / 生活常识 / 写作技法 / 世界观设定
     */
    @Column(name = "category", nullable = false, length = 30)
    private String category;

    @Column(name = "era", length = 50)
    private String era;

    @Column(name = "region", length = 50)
    private String region;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * 命中关键词，逗号分隔
     */
    @Column(name = "keywords", nullable = false, length = 500)
    private String keywords;

    @Column(name = "source", length = 200)
    private String source;

    @Column(name = "ref_count")
    private Integer refCount;

    @Column(name = "create_time")
    private LocalDateTime createTime;
}
