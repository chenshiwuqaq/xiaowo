package org.example.mynote.vo;

import lombok.Data;

import java.util.List;

/**
 * 预置 AI 服务商信息，供前端"AI 模型配置"下拉选择。
 *
 * <p>服务商与默认模型是产品内置的静态数据，放在 /api/auth/providers 免鉴权路径下，
 * 让用户还没登录也能预填配置表单；models 给全量候选而非默认项，前端展示为可选列表。</p>
 *
 * @author MyNote
 */
@Data
public class ProviderVO {

    /**
     * 服务商编码，如 deepseek，与保存配置时的 providerCode 对应
     */
    private String code;

    /**
     * 展示名称
     */
    private String name;

    /**
     * 默认接口地址，选择后可直接填入表单
     */
    private String baseUrl;

    /**
     * 预置模型名列表
     */
    private List<String> models;
}
