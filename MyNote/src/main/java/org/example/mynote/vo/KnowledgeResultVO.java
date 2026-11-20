package org.example.mynote.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 背景知识浮现结果。
 *
 * @author MyNote
 */
@Data
public class KnowledgeResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 助手模式：research/life/creation
     */
    private String mode;

    /**
     * 模式中文名
     */
    private String modeName;

    /**
     * 命中的卡片
     */
    private java.util.List<KnowledgeVO> cards;

    /**
     * 触发浮现的词
     */
    private java.util.List<String> triggerWords;

    /**
     * 说明文案，前端直接展示
     */
    private String reason;

}
