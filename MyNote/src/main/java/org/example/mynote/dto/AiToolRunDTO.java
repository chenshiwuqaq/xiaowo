package org.example.mynote.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 通用执行入参：按插件 id 运行某个工具。
 *
 * @author MyNote
 */
@Data
public class AiToolRunDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 插件 id。内置插件为短名（knowledge/references），用户插件为 user:toolId
     */
    @NotBlank(message = "要运行哪个插件？")
    private String id;

    /**
     * 笔记 id，可空（自由对话）
     */
    private Long noteId;

    /**
     * 选区文本，可空
     */
    private String selection;

    /**
     * 会话 id，可空
     */
    private Long sessionId;

    /**
     * 插件自定义参数，声明式插件暂不使用，预留给脚本插件
     */
    private Map<String, Object> params;
}
