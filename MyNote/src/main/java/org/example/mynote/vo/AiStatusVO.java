package org.example.mynote.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 助手可用状态。前端据此决定右侧面板显示「配置引导」还是「对话输入框」。
 *
 * @author MyNote
 */
@Data
public class AiStatusVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 是否已配置可用模型
     */
    private Boolean configured;

    /**
     * 当前默认模型名
     */
    private String model;

    /**
     * 服务商展示名
     */
    private String providerName;

    /**
     * 离线也可用的能力清单
     */
    private java.util.List<String> offlineScenes;

    /**
     * 需要模型的能力清单
     */
    private java.util.List<String> onlineScenes;

}
