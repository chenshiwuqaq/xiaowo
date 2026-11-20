package org.example.mynote.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 快捷写作动作入参。
 *
 * @author MyNote
 */
@Data
public class AiActionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 笔记 id
     */
    @NotNull(message = "请先打开一篇笔记")
    private Long noteId;

    /**
     * 动作：continue/polish/expand/shrink/title/summary/name/consistency/ending
     */
    @NotBlank(message = "要执行什么动作？")
    private String action;

    /**
     * 选中文字，为空时对整个笔记操作
     */
    private String selection;

    /**
     * 作者的额外要求，例如「更冷一点」
     */
    private String instruction;

    /**
     * 会话 id，用于延续上下文
     */
    private Long sessionId;

}
