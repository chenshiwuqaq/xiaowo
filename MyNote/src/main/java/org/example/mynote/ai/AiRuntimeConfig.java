package org.example.mynote.ai;

import java.math.BigDecimal;

/**
 * 模型运行时配置：从 mn_ai_config 解出的一把"可直接调用"的配置。
 *
 * <p>单独建这个类而不是把 AiConfigDO 直接丢给客户端，是为了让 apiKey 的解密动作
 * 只发生在 AiConfigService 一处，客户端拿到明文也不写日志、不外泄。</p>
 *
 * @author MyNote
 */
public class AiRuntimeConfig {

    private final String baseUrl;

    private final String modelName;

    private final String apiKey;

    private final BigDecimal temperature;

    private final Integer maxTokens;

    private final String providerName;

    public AiRuntimeConfig(String baseUrl, String modelName, String apiKey, BigDecimal temperature,
                           Integer maxTokens, String providerName) {
        this.baseUrl = baseUrl;
        this.modelName = modelName;
        this.apiKey = apiKey;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.providerName = providerName;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getModelName() {
        return modelName;
    }

    public String getApiKey() {
        return apiKey;
    }

    public BigDecimal getTemperature() {
        return temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public String getProviderName() {
        return providerName;
    }
}
