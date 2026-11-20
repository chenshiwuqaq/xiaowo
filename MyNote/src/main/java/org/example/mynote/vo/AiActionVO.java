package org.example.mynote.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 快捷动作结果。
 *
 * @author MyNote
 */
@Data
public class AiActionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 动作
     */
    private String action;

    /**
     * 模型输出
     */
    private String text;

    /**
     * 使用的模型名
     */
    private String model;

}
