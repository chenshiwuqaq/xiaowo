package org.example.mynote.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 会话概要。
 *
 * @author MyNote
 */
@Data
public class AiSessionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 会话 id
     */
    private Long id;

    /**
     * 绑定笔记 id
     */
    private Long noteId;

    /**
     * 标题
     */
    private String title;

    /**
     * 消息条数
     */
    private Integer messageCount;

    /**
     * 最后活跃时间
     */
    private java.time.LocalDateTime updateTime;

}
