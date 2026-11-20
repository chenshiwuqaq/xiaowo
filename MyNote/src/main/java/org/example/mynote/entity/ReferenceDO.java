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
 * 参考文献数据对象，对应表 mn_reference。
 *
 * <p>本地文献库为科研类笔记提供离线可用的推荐来源；联网可用时后端还会合并 OpenAlex
 * 检索到的最新文献，被引次数由接口回填。</p>
 *
 * @author MyNote
 */
@Data
@Entity
@Table(name = "mn_reference")
public class ReferenceDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "authors", length = 500)
    private String authors;

    @Column(name = "journal", length = 300)
    private String journal;

    @Column(name = "pub_year")
    private Integer pubYear;

    @Column(name = "doi", length = 200)
    private String doi;

    @Column(name = "url", length = 500)
    private String url;

    @Column(name = "field", length = 100)
    private String field;

    @Column(name = "keywords", length = 500)
    private String keywords;

    @Column(name = "abstract", columnDefinition = "TEXT")
    private String abstractText;

    @Column(name = "cited_count")
    private Integer citedCount;

    @Column(name = "create_time")
    private LocalDateTime createTime;
}
