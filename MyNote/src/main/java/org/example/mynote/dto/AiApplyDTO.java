package org.example.mynote.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户确认后的替换入参：把 revised 覆盖进笔记。
 *
 * @author MyNote
 */
@Data
public class AiApplyDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 笔记 id
     */
    @NotNull(message = "缺少笔记 id")
    private Long noteId;

    /**
     * 替换锚点原文
     */
    @NotBlank(message = "缺少替换锚点")
    private String original;

    /**
     * 替换后的新文本
     */
    @NotBlank(message = "新文本为空，无法替换")
    private String revised;

}
