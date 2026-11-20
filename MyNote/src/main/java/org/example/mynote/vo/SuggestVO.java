package org.example.mynote.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 飘窗建议。
 *
 * @author MyNote
 */
@Data
public class SuggestVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 建议问题
     */
    private java.util.List<String> suggestions;

    /**
     * 助手模式
     */
    private String mode;

    /**
     * 来源：ai / local
     */
    private String source;

}
