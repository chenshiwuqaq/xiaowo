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
 * 插件市场条目数据对象，对应表 mn_market_plugin。
 *
 * <p>发布时对插件的 manifest（名称/说明/系统提示词/用户模板等）做一次快照，
 * 与作者自己账号里的原插件解耦。like_count / comment_count / use_count 为反规范化计数，
 * 用于排行榜快速排序，明细在对应明细表中维护。</p>
 *
 * @author MyNote
 */
@Data
@Entity
@Table(name = "mn_market_plugin")
public class MarketPluginDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "author_name", nullable = false, length = 50)
    private String authorName;

    @Column(name = "author_avatar", length = 255)
    private String authorAvatar;

    @Column(name = "tool_id", nullable = false, length = 100)
    private String toolId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "render_type", nullable = false, length = 30)
    private String renderType;

    @Column(name = "system_prompt", columnDefinition = "TEXT")
    private String systemPrompt;

    @Column(name = "user_template", columnDefinition = "TEXT")
    private String userTemplate;

    @Column(name = "is_insertable")
    private Boolean insertable;

    @Column(name = "category")
    private Integer category;

    @Column(name = "tags", length = 200)
    private String tags;

    @Column(name = "status")
    private Integer status;

    @Column(name = "like_count")
    private Integer likeCount;

    @Column(name = "comment_count")
    private Integer commentCount;

    @Column(name = "use_count")
    private Integer useCount;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
