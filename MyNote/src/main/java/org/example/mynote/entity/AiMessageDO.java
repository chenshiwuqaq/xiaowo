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
 * AI 消息数据对象，对应表 mn_ai_message。
 *
 * @author MyNote
 */
@Data
@Entity
@Table(name = "mn_ai_message")
public class AiMessageDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * user / assistant / system
     */
    @Column(name = "role", nullable = false, length = 20)
    private String role;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * chat / polish / expand / knowledge / reference
     */
    @Column(name = "scene", length = 30)
    private String scene;

    @Column(name = "create_time")
    private LocalDateTime createTime;
}
