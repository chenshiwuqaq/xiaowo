package org.example.mynote.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 模型配置数据对象，对应表 mn_ai_config。
 *
 * <p>一个用户可保存多个模型并切换默认。apiKey 字段存 AES-GCM 密文，接口回显时一律掩码，
 * 不允许明文出后端。</p>
 *
 * @author MyNote
 */
@Data
@Entity
@Table(name = "mn_ai_config")
public class AiConfigDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * deepseek / openai / qianwen / kimi / zhipu / custom
     */
    @Column(name = "provider_code", nullable = false, length = 50)
    private String providerCode;

    @Column(name = "provider_name", nullable = false, length = 50)
    private String providerName;

    /**
     * OpenAI 兼容接口地址
     */
    @Column(name = "base_url", nullable = false, length = 200)
    private String baseUrl;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    /**
     * AES 密文，NULL 表示未配置 Key
     */
    @Column(name = "api_key", length = 500)
    private String apiKey;

    @Column(name = "temperature", nullable = false, precision = 3, scale = 2)
    private BigDecimal temperature;

    @Column(name = "max_tokens", nullable = false)
    private Integer maxTokens;

    @Column(name = "is_default")
    private Boolean defaultConfig;

    @Column(name = "is_enabled")
    private Boolean enabled;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
