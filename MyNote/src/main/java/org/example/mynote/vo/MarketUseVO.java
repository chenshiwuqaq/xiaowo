package org.example.mynote.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 「下载使用」后的状态回显：是否已安装进我的插件、本次是否新增了使用计数。
 *
 * @author MyNote
 */
@Data
public class MarketUseVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Boolean installed;

    private Boolean newlyUsed;

    private String toolId;

    private Integer useCount;
}
