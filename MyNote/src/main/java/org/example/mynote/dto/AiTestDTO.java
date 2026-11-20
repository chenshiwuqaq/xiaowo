package org.example.mynote.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 连通性测试入参。传 id 用已存配置，否则用表单里的临时值。
 *
 * @author MyNote
 */
@Data
public class AiTestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 已存配置 id，可空
     */
    private Long id;

    /**
     * 接口地址
     */
    private String baseUrl;

    /**
     * 模型名
     */
    private String modelName;

    /**
     * API Key；以 **** 开头视为沿用已存值
     */
    private String apiKey;

}
