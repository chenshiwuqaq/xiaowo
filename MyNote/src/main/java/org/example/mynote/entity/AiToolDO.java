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
 * 用户自定义 AI 工具插件数据对象，对应表 mn_ai_tool。
 *
 * <p>声明式插件：用户只填 manifest（名称/适用笔记类型/系统提示词/用户模板/输出方式），
 * 不需要写代码。执行时由后端组装上下文、调大模型、按 render_type 渲染。</p>
 *
 * @author MyNote
 */
@Data
@Entity
@Table(name = "mn_ai_tool")
public class AiToolDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 插件唯一标识（账号内唯一，注册表里对外展示为 user:toolId）
     */
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

    @Column(name = "is_enabled")
    private Boolean enabled;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
