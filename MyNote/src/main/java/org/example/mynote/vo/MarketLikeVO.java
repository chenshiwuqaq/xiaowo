package org.example.mynote.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 点赞 / 取消点赞后的状态回显。
 *
 * @author MyNote
 */
@Data
public class MarketLikeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Boolean liked;

    private Integer likeCount;
}
