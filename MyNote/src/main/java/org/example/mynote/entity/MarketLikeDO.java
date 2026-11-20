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
 * 插件市场点赞明细，对应表 mn_market_like。
 *
 * <p>同一用户对同一市场条目最多一条记录（唯一键 uk_plugin_user），
 * 点赞/取消点赞在事务内同步维护 {@link MarketPluginDO} 的 like_count。</p>
 *
 * @author MyNote
 */
@Data
@Entity
@Table(name = "mn_market_like")
public class MarketLikeDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plugin_id", nullable = false)
    private Long pluginId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "create_time")
    private LocalDateTime createTime;
}
