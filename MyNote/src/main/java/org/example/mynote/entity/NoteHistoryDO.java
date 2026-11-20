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
 * 笔记历史版本数据对象，对应表 mn_note_history。
 *
 * <p>AI 直接改写正文前先写一份快照，用户不满意时可一键回退，避免误覆盖造成不可逆损失。</p>
 *
 * @author MyNote
 */
@Data
@Entity
@Table(name = "mn_note_history")
public class NoteHistoryDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "note_id", nullable = false)
    private Long noteId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "content", columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "word_count")
    private Integer wordCount;

    /**
     * save 手动保存 / ai AI 覆盖前 / import 导入
     */
    @Column(name = "source", nullable = false, length = 20)
    private String source;

    @Column(name = "create_time")
    private LocalDateTime createTime;
}
