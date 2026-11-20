package org.example.mynote.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 插件市场条目（卡片）视图对象。
 *
 * <p>列表、排行榜、随机换一批、我的发布均复用此结构；{@link MarketPluginDetailVO} 在其基础上
 * 追加 manifest 全文与评论。</p>
 *
 * @author MyNote
 */
@Data
public class MarketPluginVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long authorId;

    private String authorName;

    private String authorAvatar;

    private String toolId;

    private String name;

    private String description;

    private String renderType;

    private Boolean insertable;

    private Integer category;

    private String categoryName;

    private List<String> tags;

    private Integer likeCount;

    private Integer commentCount;

    private Integer useCount;

    /**
     * 状态：1上架 0下架（仅「我的发布」有意义，公开列表恒为 1）
     */
    private Integer status;

    /**
     * 当前用户是否点过爱心
     */
    private Boolean liked;

    /**
     * 当前用户是否使用/安装过
     */
    private Boolean used;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
