package org.example.mynote.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户声明式插件的保存入参。
 *
 * @author MyNote
 */
@Data
public class AiToolSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键；更新时传入
     */
    private Long id;

    /**
     * 插件标识，账号内唯一
     */
    @NotBlank(message = "插件标识不能为空")
    @Size(max = 100, message = "插件标识太长")
    private String toolId;

    /**
     * 展示名
     */
    @NotBlank(message = "插件名称不能为空")
    @Size(max = 100, message = "插件名称太长")
    private String name;

    /**
     * 一句话说明
     */
    @Size(max = 500, message = "说明太长")
    private String description;

    /**
     * 渲染类型，当前仅支持 text
     */
    private String renderType;

    /**
     * 系统提示词
     */
    private String systemPrompt;

    /**
     * 用户模板，支持 {{note}} {{selection}} {{siblings}} {{title}}
     */
    private String userTemplate;

    /**
     * 结果是否可插入正文
     */
    private Boolean insertable;

    /**
     * 排序号
     */
    private Integer sortOrder;
}
