package org.example.mynote.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 文献推荐结果。
 *
 * @author MyNote
 */
@Data
public class ReferenceResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 助手模式
     */
    private String mode;

    /**
     * 从笔记里抽出的检索词
     */
    private java.util.List<String> keywords;

    /**
     * 文献列表
     */
    private java.util.List<ReferenceVO> items;

    /**
     * 是否合并了联网检索结果
     */
    private Boolean online;

    /**
     * 说明文案
     */
    private String reason;

    /**
     * 联网检索失败时的具体原因（网络不通 / 超时 / 无相关结果等），成功时为空
     */
    private String onlineError;

}
