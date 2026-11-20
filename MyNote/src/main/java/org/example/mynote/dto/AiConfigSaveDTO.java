package org.example.mynote.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 新增或修改模型配置。apiKey 传空串表示不修改已有值。
 *
 * @author MyNote
 */
@Data
public class AiConfigSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 配置 id，为空表示新增
     */
    private Long id;

    /**
     * 服务商标识
     */
    @NotBlank(message = "请选择服务商")
    private String providerCode;

    /**
     * 服务商展示名
     */
    private String providerName;

    /**
     * OpenAI 兼容接口地址
     */
    @NotBlank(message = "请填写接口地址")
    private String baseUrl;

    /**
     * 模型名
     */
    @NotBlank(message = "请填写模型名")
    private String modelName;

    /**
     * API Key 明文，服务端加密后落库；传空串表示保持原值
     */
    private String apiKey;

    /**
     * 采样温度 0~2
     */
    private java.math.BigDecimal temperature;

    /**
     * 最大输出 token
     */
    private Integer maxTokens;

    /**
     * 是否设为默认模型
     */
    private Boolean defaultConfig;

}
