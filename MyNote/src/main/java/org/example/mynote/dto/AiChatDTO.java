package org.example.mynote.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import jakarta.validation.constraints.NotBlank;

/**
 * AI 对话入参。sessionId 为空表示开新会话。
 *
 * @author MyNote
 */
@Data
public class AiChatDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 绑定的笔记 id，可为空（自由对话）
     */
    private Long noteId;

    /**
     * 会话 id，为空则新建会话
     */
    private Long sessionId;

    /**
     * 用户的问题
     */
    @NotBlank(message = "想问什么都还没写呢")
    @Size(max = 2000, message = "单条提问不要超过 2000 字")
    private String question;

}
