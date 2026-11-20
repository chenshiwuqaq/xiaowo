package org.example.mynote.controller;

import org.example.mynote.common.Result;
import org.example.mynote.common.ResultCode;
import org.example.mynote.service.GreetingService;
import org.example.mynote.service.StatsService;
import org.example.mynote.service.WeatherService;
import org.example.mynote.vo.GreetingVO;
import org.example.mynote.vo.HomeVO;
import org.example.mynote.vo.TrendVO;
import org.example.mynote.vo.WeatherVO;
import org.example.mynote.vo.WritingCalendarVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 主页看板接口。
 *
 * <p>四个统计类接口全部只读、只依赖登录态，不接受 userId 参数，用户身份由拦截器写入的
 * {@code UserContext} 决定。参数越界一律钳制而不报错：这些值来自前端组件，
 * 传错一个数字不该让首屏变成错误码。</p>
 *
 * @author MyNote
 */
@RestController
@RequestMapping("/api/home")
public class HomeController {

    /**
     * 热力图默认窗口天数，与主页卡片一致
     */
    private static final int DEFAULT_CALENDAR_DAYS = 120;

    /**
     * 趋势图默认窗口天数
     */
    private static final int DEFAULT_TREND_DAYS = 30;

    /**
     * 天气不可用时的提示语，前端可直接展示
     */
    private final StatsService statsService;

    private final WeatherService weatherService;

    private final GreetingService greetingService;

    public HomeController(StatsService statsService, WeatherService weatherService,
                          GreetingService greetingService) {
        this.statsService = statsService;
        this.weatherService = weatherService;
        this.greetingService = greetingService;
    }

    /**
     * 主页总览：欢迎信息、字数口径、连续打卡、类型分布、最近与置顶笔记、热力图、趋势、天气。
     *
     * <p>天气在这一步"尽力而为"：拿不到就留空字段，其余统计照常返回。这是主页与
     * 独立天气接口的关键差别——看板不能被一次出网失败拖垮。</p>
     *
     * @return 主页数据
     */
    @GetMapping("/overview")
    public Result<HomeVO> overview() {
        HomeVO vo = statsService.overview();
        vo.setWeather(weatherService.currentWeather());
        return Result.ok(vo);
    }

    /**
     * 书写热力图数据，日期以今天为最后一天向前推 days 天，没写字的天补 0。
     *
     * @param days 天数，1~366，越界钳制
     * @return 连续日期序列
     */
    @GetMapping("/writing-calendar")
    public Result<WritingCalendarVO> writingCalendar(
            @RequestParam(value = "days", defaultValue = "" + DEFAULT_CALENDAR_DAYS) int days) {
        return Result.ok(statsService.writingCalendar(days));
    }

    /**
     * 字数趋势数据，附带合计与单日峰值。
     *
     * @param days 天数，1~366，越界钳制
     * @return 趋势序列与统计量
     */
    @GetMapping("/trend")
    public Result<TrendVO> trend(
            @RequestParam(value = "days", defaultValue = "" + DEFAULT_TREND_DAYS) int days) {
        return Result.ok(statsService.trend(days));
    }

    /**
     * 单独取当前用户城市的天气。
     *
     * <p>上游不可用时返回业务码 3001（可忽略的失败）与提示文案、data 为空，
     * 前端据此把卡片切成占位状态。这里刻意不走异常：天气失败是可预期的常态，
     * 不该在全局异常处理里留下堆栈。同时必须说明，这个 500 只是 HTTP 信封里的业务 code，
     * 响应状态仍是 200，不会波及主页其他区域。</p>
     *
     * @return 天气数据，或取数失败提示
     */
    @GetMapping("/weather")
    public Result<WeatherVO> weather() {
        WeatherVO weather = weatherService.currentWeather();
        if (weather == null) {
            return Result.fail(ResultCode.WEATHER_UNAVAILABLE);
        }
        return Result.ok(weather);
    }

    /**
     * 按时段生成的开场白与小贴士。
     *
     * @return 问候语、日期副标题与提示列表
     */
    @GetMapping("/greeting")
    public Result<GreetingVO> greeting() {
        return Result.ok(greetingService.greet());
    }
}
