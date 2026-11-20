package org.example.mynote.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 书写日志数据对象，对应表 mn_writing_log。主页看板、连续打卡与热力图的数据源。
 *
 * @author MyNote
 */
@Data
@Entity
@Table(name = "mn_writing_log")
public class WritingLogDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "note_id", nullable = false)
    private Long noteId;

    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    /**
     * 当日字数增量，可为负（删改）
     */
    @Column(name = "word_delta", nullable = false)
    private Integer wordDelta;

    @Column(name = "create_time")
    private LocalDateTime createTime;
}
