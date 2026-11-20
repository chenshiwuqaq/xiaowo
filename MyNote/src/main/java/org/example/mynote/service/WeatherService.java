package org.example.mynote.service;

import org.example.mynote.vo.WeatherVO;

/**
 * 天气代理服务，上游为 Open-Meteo（免 API Key）。
 *
 * <p>本接口的契约是"拿不到就返回 null"：主页属于阅读场景，天气抓不到只该让那一块卡片空着，
 * 不能因为一次出网失败把整页统计一起拖倒，所以实现里所有异常都在服务内部消化。</p>
 *
 * @author MyNote
 */
public interface WeatherService {

    /**
     * 取当前登录用户设置城市的实时天气与 5 天预报。
     *
     * <p>命中内存缓存时不产生网络请求。城市由服务端按 userId 从用户表读取，不接受前端传参，
     * 避免这个接口被当成免费的天气代理滥用。</p>
     *
     * @return 天气数据；用户未设置城市、上游超时或返回结构异常时返回 null
     */
    WeatherVO currentWeather();
}
