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
 * 笔记逻辑图数据对象，对应表 mn_diagram。存 Mermaid 源码，前端渲染后可再次编辑。
 *
 * @author MyNote
 */
@Data
@Entity
@Table(name = "mn_diagram")
public class DiagramDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /**
     * mindmap / flowchart / graph / timeline
     */
    @Column(name = "diagram_type", nullable = false, length = 30)
    private String diagramType;

    /**
     * 参与生成的笔记 id，逗号分隔
     */
    @Column(name = "note_ids", length = 500)
    private String noteIds;

    @Column(name = "code", nullable = false, columnDefinition = "TEXT")
    private String code;

    @Column(name = "create_time")
    private LocalDateTime createTime;
}
