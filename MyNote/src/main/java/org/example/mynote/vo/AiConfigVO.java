package org.example.mynote.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 模型配置回显，apiKey 永远是掩码。
 *
 * @author MyNote
 */
@Data
public class AiConfigVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 配置 id
     */
    private Long id;

    /**
     * 服务商标识
     */
    private String providerCode;

    /**
     * 服务商展示名
     */
    private String providerName;

    /**
     * 接口地址
     */
    private String baseUrl;

    /**
     * 模型名
     */
    private String modelName;

    /**
     * API Key 掩码，未配置为 null
     */
    private String apiKeyMask;

    /**
     * 是否已填写 Key
     */
    private Boolean hasKey;

    /**
     * 采样温度
     */
    private java.math.BigDecimal temperature;

    /**
     * 最大输出 token
     */
    private Integer maxTokens;

    /**
     * 是否默认
     */
    private Boolean defaultConfig;

    /**
     * 是否启用
     */
    private Boolean enabled;

}
