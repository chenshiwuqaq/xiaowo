package org.example.mynote.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 改写请求入参：返回新文本但不落库，等用户确认。
 *
 * @author MyNote
 */
@Data
public class AiRewriteDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 笔记 id
     */
    @NotNull(message = "请先打开一篇笔记")
    private Long noteId;

    /**
     * 动作：polish/expand/shrink
     */
    @NotBlank(message = "缺少动作")
    private String action;

    /**
     * 要被替换的原文
     */
    @NotBlank(message = "先选中要修改的文字")
    private String selection;

    /**
     * 作者的额外要求
     */
    private String instruction;

}
