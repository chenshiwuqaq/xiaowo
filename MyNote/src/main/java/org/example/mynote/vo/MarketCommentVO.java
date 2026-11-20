package org.example.mynote.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 插件市场评论视图对象。
 *
 * @author MyNote
 */
@Data
public class MarketCommentVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long pluginId;

    private Long userId;

    private String userName;

    private String userAvatar;

    private String content;

    private LocalDateTime createTime;
}
