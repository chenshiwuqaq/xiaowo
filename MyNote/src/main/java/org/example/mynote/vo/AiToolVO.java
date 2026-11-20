package org.example.mynote.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 助手面板的插件清单项（内置与用户插件合并后的活跃清单）。
 *
 * @author MyNote
 */
@Data
public class AiToolVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 有效 id：内置为短名，用户插件为 user:toolId
     */
    private String id;

    /**
     * 来源：builtin / user
     */
    private String origin;

    private String name;

    private String description;

    /**
     * 渲染类型：text / cards / references
     */
    private String renderType;

    /**
     * 是否依赖模型
     */
    private Boolean requiresModel;

    /**
     * 文本结果是否可插入正文
     */
    private Boolean insertable;
}
