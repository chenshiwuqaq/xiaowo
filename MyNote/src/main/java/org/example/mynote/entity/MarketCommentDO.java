package org.example.mynote.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 插件市场评论，对应表 mn_market_comment。
 *
 * <p>昵称与头像做快照冗余，列表页展示评论时无需再 join 用户表。</p>
 *
 * @author MyNote
 */
@Data
@Entity
@Table(name = "mn_market_comment")
public class MarketCommentDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plugin_id", nullable = false)
    private Long pluginId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_name", nullable = false, length = 50)
    private String userName;

    @Column(name = "user_avatar", length = 255)
    private String userAvatar;

    @Column(name = "content", nullable = false, length = 1000)
    private String content;

    @Column(name = "create_time")
    private LocalDateTime createTime;
}
