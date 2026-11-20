package org.example.mynote.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 改写预览。前端据此渲染 diff，用户确认后再调 apply。
 *
 * @author MyNote
 */
@Data
public class RewriteVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 原文
     */
    private String original;

    /**
     * 新文本
     */
    private String revised;

    /**
     * 改动说明
     */
    private String explanation;

    /**
     * 追问建议：改写后主动抛出的"要不要再往某方向改"的方向提示，供前端一键进入下一轮
     */
    private String followUp;

    /**
     * 使用的模型名
     */
    private String model;

}
