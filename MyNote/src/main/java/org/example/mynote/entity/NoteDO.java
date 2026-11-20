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
 * 笔记数据对象，对应表 mn_note。正文以 Markdown 源文本存储。
 *
 * @author MyNote
 */
@Data
@Entity
@Table(name = "mn_note")
public class NoteDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "folder_id", nullable = false)
    private Long folderId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /**
     * Markdown 正文，长文本
     */
    @Column(name = "content", columnDefinition = "LONGTEXT")
    private String content;

    /**
     * 摘要，供 AI 组装同目录上下文使用
     */
    @Column(name = "summary", length = 500)
    private String summary;

    /**
     * 1 科研，2 生活，3 小说，4 随笔。决定 AI 助手的行为分支。
     */
    @Column(name = "note_type")
    private Integer noteType;

    @Column(name = "word_count")
    private Integer wordCount;

    /**
     * 创作情绪标签，主页开场白使用
     */
    @Column(name = "mood", length = 20)
    private String mood;

    @Column(name = "is_top")
    private Boolean top;

    @Column(name = "is_deleted")
    private Boolean deleted;

    @Column(name = "delete_time")
    private LocalDateTime deleteTime;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
