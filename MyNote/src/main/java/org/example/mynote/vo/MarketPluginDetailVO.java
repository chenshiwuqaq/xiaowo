package org.example.mynote.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 插件市场条目详情（含 manifest 全文与评论）。
 *
 * @author MyNote
 */
@Data
public class MarketPluginDetailVO extends MarketPluginVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String systemPrompt;

    private String userTemplate;

    private List<MarketCommentVO> comments;
}
