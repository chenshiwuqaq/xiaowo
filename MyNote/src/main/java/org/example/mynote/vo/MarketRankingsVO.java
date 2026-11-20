package org.example.mynote.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 市场排行榜：三张榜单（爱心最多 / 评论最多 / 使用最多）。
 *
 * @author MyNote
 */
@Data
public class MarketRankingsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<MarketPluginVO> byLike;

    private List<MarketPluginVO> byComment;

    private List<MarketPluginVO> byUse;
}
