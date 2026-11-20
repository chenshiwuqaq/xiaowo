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
 * 笔记与参考文献关联数据对象，对应表 mn_note_reference。
 *
 * <p>记录某篇科研笔记推荐/采纳过哪些文献，source 区分 AI 推荐与手动添加。</p>
 *
 * @author MyNote
 */
@Data
@Entity
@Table(name = "mn_note_reference")
public class NoteReferenceDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "note_id", nullable = false)
    private Long noteId;

    @Column(name = "ref_id", nullable = false)
    private Long refId;

    /**
     * ai 推荐 / manual 手动添加
     */
    @Column(name = "source", nullable = false, length = 20)
    private String source;

    @Column(name = "create_time")
    private LocalDateTime createTime;
}
