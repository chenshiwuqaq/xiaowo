package org.example.mynote.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户设置更新入参。
 *
 * <p>只包含契约允许的六个显示/习惯类字段，account、status、avatar 均不在其中——
 * 设置接口不应该改动账号或账号状态。数值型字段（dailyGoal、fontSize）这里只做非空校验，
 * 具体范围校验放在 Service 里抛 BizException，以便给出中文区间提示；
 * 若改用 @Min/@Max，越界会被参数校验拦成统一提示，达不到写清范围的要求。</p>
 *
 * @author MyNote
 */
@Data
public class SettingsDTO {

    /**
     * 主题强调色，形如 #C06A46
     */
    @NotBlank(message = "主题色不能为空")
    @Size(max = 20, message = "主题色长度不能超过20个字符")
    private String themeColor;

    /**
     * 主题模式：light / dark
     */
    @NotBlank(message = "主题模式不能为空")
    @Size(max = 20, message = "主题模式长度不能超过20个字符")
    private String themeMode;

    /**
     * 笔记书页背景标识或自定义图片路径
     */
    @NotBlank(message = "笔记背景不能为空")
    @Size(max = 255, message = "笔记背景长度不能超过255个字符")
    private String noteBg;

    /**
     * 天气城市
     */
    @NotBlank(message = "天气城市不能为空")
    @Size(max = 50, message = "天气城市长度不能超过50个字符")
    private String weatherCity;

    /**
     * 每日字数目标，范围 0~20000，越界由 Service 抛出带区间的中文提示
     */
    @NotNull(message = "每日字数目标不能为空")
    private Integer dailyGoal;

    /**
     * 正文字号，范围 12~24，越界由 Service 抛出带区间的中文提示
     */
    @NotNull(message = "字号不能为空")
    private Integer fontSize;

    /**
     * 笔记类型 × AI 功能开关的 JSON 配置，可空（缺失或损坏时前端回落默认值）
     */
    @Size(max = 2000, message = "AI 功能配置长度不能超过2000个字符")
    private String aiFeatureMap;
}
