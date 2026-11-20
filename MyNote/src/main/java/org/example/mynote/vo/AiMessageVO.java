package org.example.mynote.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 会话消息。
 *
 * @author MyNote
 */
@Data
public class AiMessageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息 id
     */
    private Long id;

    /**
     * 角色 user/assistant
     */
    private String role;

    /**
     * 内容
     */
    private String content;

    /**
     * 场景
     */
    private String scene;

    /**
     * 时间
     */
    private java.time.LocalDateTime createTime;

}
