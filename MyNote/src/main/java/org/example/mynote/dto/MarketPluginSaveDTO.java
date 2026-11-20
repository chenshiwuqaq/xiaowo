package org.example.mynote.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 插件市场的发布 / 更新入参。
 *
 * <p>支持两种来源：</p>
 * <ul>
 *   <li>从「我的插件」发布：传 {@code sourceToolId}（mn_ai_tool 主键），后端据此拷贝 manifest；</li>
 *   <li>直接粘贴 manifest：直接填 name / toolId / systemPrompt 等字段。</li>
 * </ul>
 *
 * @author MyNote
 */
@Data
public class MarketPluginSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键；更新时传入
     */
    private Long id;

    /**
     * 从「我的插件」发布时，指向的 mn_ai_tool 主键
     */
    private Long sourceToolId;

    @Size(max = 100, message = "插件标识太长")
    private String toolId;

    @Size(max = 100, message = "插件名称太长")
    private String name;

    @Size(max = 500, message = "说明太长")
    private String description;

    private String renderType;

    private String systemPrompt;

    private String userTemplate;

    private Boolean insertable;

    /**
     * 分类：0通用 1科研 2生活 3小说 4随笔
     */
    @NotNull(message = "请选择插件分类")
    private Integer category;

    /**
     * 自由标签，逗号分隔
     */
    @Size(max = 200, message = "标签太长")
    private String tags;
}
