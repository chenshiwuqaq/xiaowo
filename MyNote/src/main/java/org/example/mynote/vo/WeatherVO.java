package org.example.mynote.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 主页天气卡片数据，来源为 Open-Meteo（免密钥）。
 *
 * <p>这个对象"为 null 或字段缺失"是正常状态而非错误：断网、城市没配、上游超时都会让后端拿不到数据，
 * 但主页其余部分照常可用，所以由控制器把该字段留空，而不是让整个 overview 接口失败。
 * 前端应在 weather 缺失时显示"天气暂不可用"。</p>
 *
 * <p>icon 是稳定的英文标识（如 clear、light_snow），与中文描述 weatherText 分开返回：
 * 换一套天气图标只改前端资源，不用后端重新发版。</p>
 *
 * @author MyNote
 */
@Data
public class WeatherVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 实际命中的城市中文名（上游地理编码返回，可能与用户输入略有出入，如"杭州市"）
     */
    private String city;

    /**
     * 国家名，地理编码无结果时为 null
     */
    private String country;

    /**
     * 当前气温，摄氏度
     */
    private Double temperature;

    /**
     * 体感温度，摄氏度
     */
    private Double feelsLike;

    /**
     * 相对湿度，百分比整数部分
     */
    private Integer humidity;

    /**
     * 风速，km/h
     */
    private Double windSpeed;

    /**
     * WMO 天气现象码，保留原码便于前端特殊处理
     */
    private Integer weatherCode;

    /**
     * 天气现象中文描述，未知码归"其他"
     */
    private String weatherText;

    /**
     * 图标标识，前端据此选图
     */
    private String icon;

    /**
     * 本条数据的采集时刻（不是请求时刻）：命中缓存时仍是当初抓取的时间，
     * 便于前端如实告诉用户"这是 30 分钟前看到的天气"。格式 yyyy-MM-dd HH:mm:ss
     */
    private String updatedAt;

    /**
     * 未来 5 天预报，上游未返回时为空数组
     */
    private List<Forecast> forecast;

    /**
     * 单日预报。作为内部类而不是单独文件，是因为它只在天气卡片里出现，
     * 拆出去反而多一个可被误用的公共类型。
     */
    @Data
    public static class Forecast implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 日期，yyyy-MM-dd，直接沿用上游字符串
         */
        private String date;

        /**
         * 当天天气现象中文描述
         */
        private String text;

        /**
         * 当天最低温，摄氏度
         */
        private Double min;

        /**
         * 当天最高温，摄氏度
         */
        private Double max;

        /**
         * 图标标识
         */
        private String icon;
    }
}
