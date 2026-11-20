package org.example.mynote.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.mynote.common.BizException;
import org.example.mynote.common.ResultCode;
import org.example.mynote.common.UserContext;
import org.example.mynote.config.AppProperties;
import org.example.mynote.dao.UserDAO;
import org.example.mynote.entity.UserDO;
import org.example.mynote.service.WeatherService;
import org.example.mynote.vo.WeatherVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Open-Meteo 天气代理实现（免 API Key）。
 *
 * <p>两步取数：先用地理编码把中文城市名换成经纬度，再按经纬度取实况与 5 天预报。
 * 分成两次请求是上游的接口形状决定的，也顺带把"城市名写错"与"临时连不上"区分开——
 * 前者地理编码就返回空，后者只在请求阶段抛错。</p>
 *
 * <p><b>容错优先</b>：本类对外的唯一约定是"拿不到就返回 null"。所有出网异常（超时、非 200、
 * JSON 结构意外、配置缺 URL）都在方法内部消化并只记 warn，绝不让主页 500；
 * 天气是锦上添花，看板数据才是主页的正事。</p>
 *
 * <p><b>缓存</b>：进程内 ConcurrentHashMap，key 为城市名，TTL 取 mynote.weather.cache-minutes。
 * 项目不引入 Redis，单机自托管下多一层中间件只会多一份故障点；天气数据本身半小时才变一次，
 * 内存缓存足够。只缓存成功结果：失败原因多为瞬时网络抖动，缓存失败会让用户长时间只看空白卡片。</p>
 *
 * @author MyNote
 */
@Service
public class WeatherServiceImpl implements WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherServiceImpl.class);

    /**
     * 预报天数，主页卡片只排得下 5 天
     */
    private static final int FORECAST_DAYS = 5;

    /**
     * 地理编码查询模板，name 需 URL 编码后填入
     */
    private static final String GEOCODING_QUERY = "?name=%s&count=1&language=zh";

    /**
     * 预报查询模板：经纬度 + 需要的当前字段与逐日字段
     */
    private static final String FORECAST_QUERY = "?latitude=%s&longitude=%s"
            + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m"
            + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
            + "&timezone=Asia/Shanghai&forecast_days=" + FORECAST_DAYS;

    /**
     * 建立连接超时。上游在国内偶发慢速握手，卡在这里不如快速放弃
     */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    /**
     * 单次请求读取超时
     */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);

    /**
     * 缓存的城市条数上限。实际只有一个用户，留这个上限只为防止别人拿接口当天气代理刷
     */
    private static final int MAX_CACHE_CITIES = 32;

    private static final int HTTP_STATUS_OK = 200;

    /**
     * 未知天气码的归类
     */
    private static final String UNKNOWN_WEATHER_TEXT = "其他";

    private static final String UNKNOWN_WEATHER_ICON = "other";

    /**
     * 上游数值字段缺失时用 NaN 标记，便于与"真的是 0 度"区分开
     */
    private static final double NOT_A_NUMBER = Double.NaN;

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * city(城市名) -> 缓存条目
     */
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private final HttpClient httpClient;

    private final ObjectMapper objectMapper;

    private final UserDAO userDAO;

    private final AppProperties appProperties;

    public WeatherServiceImpl(ObjectMapper objectMapper, UserDAO userDAO, AppProperties appProperties) {
        this.objectMapper = objectMapper;
        this.userDAO = userDAO;
        this.appProperties = appProperties;
        this.httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>方法体内不再向外抛网络与解析类异常：出网失败、超时、非 200、JSON 结构意外、
     * 配置缺 URL 全部收敛成 null。两个例外是刻意保留的——业务异常（登录态失效）要继续按未登录处理，
     * 不能被伪装成"天气取不到"；线程中断要恢复标记。这两类都跟上游健康度无关。</p>
     */
    @Override
    public WeatherVO currentWeather() {
        try {
            String city = resolveCity();
            if (city == null) {
                return null;
            }
            CacheEntry cached = cache.get(city);
            if (cached != null) {
                if (isFresh(cached)) {
                    return cached.weather;
                }
                // 带上期望值删除：另一线程刚写入的新条目不会被我们误删
                cache.remove(city, cached);
            }
            WeatherVO fetched = fetchUpstream(city);
            if (fetched != null) {
                putCache(city, fetched);
            }
            return fetched;
        } catch (BizException e) {
            throw e;
        } catch (InterruptedException e) {
            // 请求被中断通常是要停服了，恢复标记让上层能感知取消，但不必把异常扩散出去
            Thread.currentThread().interrupt();
            log.warn("天气请求被中断");
            return null;
        } catch (Exception e) {
            log.warn("天气获取失败，主页天气留空：{}", e.toString());
            return null;
        }
    }

    /**
     * 取当前用户设置的城市名，同时作为缓存 key。
     *
     * @return 去空白的城市名；没配城市或上游 URL 未配置时返回 null
     */
    private String resolveCity() {
        Long userId = UserContext.require();
        // 账号查不到属于登录态问题，按未登录抛出而不是伪装成"天气取不到"
        UserDO user = userDAO.findById(userId)
                .orElseThrow(() -> new BizException(ResultCode.UNAUTHORIZED, "登录状态已失效，请重新登录"));
        String city = user.getWeatherCity();
        if (city == null || city.isBlank()) {
            return null;
        }
        AppProperties.Weather weatherConfig = appProperties.getWeather();
        if (isBlank(weatherConfig.getGeocodingUrl()) || isBlank(weatherConfig.getForecastUrl())) {
            log.warn("天气跳过：mynote.weather 的 geocoding-url / forecast-url 未配置");
            return null;
        }
        return city.trim();
    }

    /**
     * 走一趟上游：地理编码 → 预报。
     *
     * @param city 城市名
     * @return 天气数据；城市无法解析或上游字段缺失时返回 null
     * @throws IOException         网络失败、非 200
     * @throws InterruptedException 请求线程被中断
     */
    private WeatherVO fetchUpstream(String city) throws IOException, InterruptedException {
        AppProperties.Weather weatherConfig = appProperties.getWeather();
        JsonNode geoPoint = geocode(city, weatherConfig.getGeocodingUrl());
        if (geoPoint == null) {
            log.warn("地理编码未命中城市：{}", city);
            return null;
        }
        double latitude = geoPoint.path("latitude").asDouble(NOT_A_NUMBER);
        double longitude = geoPoint.path("longitude").asDouble(NOT_A_NUMBER);
        if (Double.isNaN(latitude) || Double.isNaN(longitude)) {
            log.warn("地理编码缺少经纬度：{}", city);
            return null;
        }

        String forecastUrl = weatherConfig.getForecastUrl() + String.format(FORECAST_QUERY,
                String.valueOf(latitude), String.valueOf(longitude));
        JsonNode forecast = getJson(forecastUrl);

        WeatherVO vo = new WeatherVO();
        // 城市名以上游返回的中文名为准，用户写"杭州"或"杭州市"都能看到规范写法
        String matchedName = geoPoint.path("name").asText(null);
        vo.setCity(matchedName == null ? city : matchedName);
        vo.setCountry(geoPoint.path("country").asText(null));

        JsonNode current = forecast.path("current");
        vo.setTemperature(doubleOrNull(current.path("temperature_2m")));
        vo.setFeelsLike(doubleOrNull(current.path("apparent_temperature")));
        Double humidity = doubleOrNull(current.path("relative_humidity_2m"));
        vo.setHumidity(humidity == null ? null : (int) Math.round(humidity));
        vo.setWindSpeed(doubleOrNull(current.path("wind_speed_10m")));
        Double code = doubleOrNull(current.path("weather_code"));
        Integer weatherCode = code == null ? null : (int) Math.round(code);
        vo.setWeatherCode(weatherCode);
        WmoWeather kind = mapWmo(weatherCode);
        vo.setWeatherText(kind.text());
        vo.setIcon(kind.icon());
        vo.setForecast(buildForecastList(forecast.path("daily")));
        // 采集时刻而非请求时刻：命中缓存时前端仍能显示"这是半小时前的天气"
        vo.setUpdatedAt(LocalDateTime.now().format(DATE_TIME_FORMATTER));
        return vo;
    }

    /**
     * 地理编码：只要第一个结果。count=1 已经把上游裁到一条，不再做择优。
     *
     * @param city 城市名
     * @param baseUrl 配置好的地理编码地址
     * @return 命中节点；无结果时 null
     * @throws IOException         网络失败
     * @throws InterruptedException 请求被中断
     */
    private JsonNode geocode(String city, String baseUrl) throws IOException, InterruptedException {
        // URLEncoder 是表单编码，空格会变成 +，这在 query 里等价于 %20，上游可正常解析
        String encoded = URLEncoder.encode(city, StandardCharsets.UTF_8);
        JsonNode root = getJson(baseUrl + String.format(GEOCODING_QUERY, encoded));
        JsonNode results = root.path("results");
        if (!results.isArray() || results.isEmpty()) {
            return null;
        }
        return results.get(0);
    }

    /**
     * 发一个 GET 请求并把响应读成 JSON 树。非 200 一律按失败处理，因为上游错误时返回的是
     * 一段 reason 文本，硬解析出的空对象会让天气卡片显示一堆 0。
     *
     * @param url 完整地址
     * @return JSON 根节点
     * @throws IOException         连接失败、状态码非 200、响应不是 JSON
     * @throws InterruptedException 请求被中断
     */
    private JsonNode getJson(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != HTTP_STATUS_OK) {
            throw new IOException("上游状态码 " + response.statusCode());
        }
        return objectMapper.readTree(response.body());
    }

    /**
     * 逐日预报。
     *
     * <p>上游按数组下标对齐日期与各字段，任一数组缺失或长度不齐就整份预报作废：
     * 拼出"今天 25 度明天 0 度"这种错位数据，比干脆没有更糟。</p>
     *
     * @param daily daily 节点
     * @return 预报列表，拿不到时为空列表
     */
    private List<WeatherVO.Forecast> buildForecastList(JsonNode daily) {
        JsonNode times = daily.path("time");
        JsonNode codes = daily.path("weather_code");
        JsonNode maxes = daily.path("temperature_2m_max");
        JsonNode mins = daily.path("temperature_2m_min");
        if (!times.isArray() || times.isEmpty()) {
            return Collections.emptyList();
        }
        int size = times.size();
        size = Math.min(size, codes.size());
        size = Math.min(size, maxes.size());
        size = Math.min(size, mins.size());
        if (size <= 0) {
            return Collections.emptyList();
        }
        List<WeatherVO.Forecast> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            WeatherVO.Forecast item = new WeatherVO.Forecast();
            item.setDate(times.get(i).asText(null));
            Double code = doubleOrNull(codes.get(i));
            WmoWeather kind = mapWmo(code == null ? null : (int) Math.round(code));
            item.setText(kind.text());
            item.setIcon(kind.icon());
            item.setMax(doubleOrNull(maxes.get(i)));
            item.setMin(doubleOrNull(mins.get(i)));
            list.add(item);
        }
        return list;
    }

    /**
     * WMO 天气现象码 → 中文描述 + 图标标识。
     *
     * <p>码表是国际通用的固定标准，不会随配置变，所以写成代码内的静态映射而不是 yml；
     * 集中一处便于前端只要按 icon 取图、按 text 展示，描述口径全站一致。
     * 上游将来新增码值时归入"其他"，不会因为一个陌生数字让天气整块消失。</p>
     *
     * @param code 天气码，可为 null
     * @return 中文描述与图标标识
     */
    private static WmoWeather mapWmo(Integer code) {
        if (code == null) {
            return new WmoWeather(UNKNOWN_WEATHER_TEXT, UNKNOWN_WEATHER_ICON);
        }
        return switch (code) {
            case 0 -> new WmoWeather("晴", "clear");
            case 1 -> new WmoWeather("大部晴朗", "mostly_clear");
            case 2 -> new WmoWeather("局部多云", "partly_cloudy");
            case 3 -> new WmoWeather("阴", "overcast");
            case 45, 48 -> new WmoWeather("雾", "fog");
            case 51 -> new WmoWeather("小毛毛雨", "drizzle");
            case 53 -> new WmoWeather("毛毛雨", "drizzle");
            case 55 -> new WmoWeather("强毛毛雨", "drizzle");
            case 56, 57 -> new WmoWeather("冻毛毛雨", "freezing_drizzle");
            case 61 -> new WmoWeather("小雨", "rain");
            case 62 -> new WmoWeather("中雨", "rain");
            case 63 -> new WmoWeather("大雨", "rain");
            case 64, 65 -> new WmoWeather("强降水", "heavy_rain");
            case 66, 67 -> new WmoWeather("冻雨", "freezing_rain");
            case 71 -> new WmoWeather("小雪", "light_snow");
            case 72 -> new WmoWeather("中雪", "snow");
            case 73 -> new WmoWeather("大雪", "heavy_snow");
            case 74 -> new WmoWeather("轻冰霰", "snow");
            case 75 -> new WmoWeather("强冰霰", "snow");
            case 77 -> new WmoWeather("雪粒", "snow");
            case 80 -> new WmoWeather("小阵雨", "shower");
            case 81 -> new WmoWeather("阵雨", "shower");
            case 82 -> new WmoWeather("强阵雨", "shower");
            case 85 -> new WmoWeather("小阵雪", "snow_shower");
            case 86 -> new WmoWeather("阵雪", "snow_shower");
            case 95 -> new WmoWeather("雷阵雨", "thunderstorm");
            case 96, 99 -> new WmoWeather("雷阵雨伴冰雹", "thunderstorm_hail");
            default -> new WmoWeather(UNKNOWN_WEATHER_TEXT, UNKNOWN_WEATHER_ICON);
        };
    }

    /**
     * 缺失或非数值节点返回 null，让 VO 字段留空而不是把"没有数据"渲染成 0 度。
     *
     * @param node 节点
     * @return 数值或 null
     */
    private static Double doubleOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        double value = node.asDouble(NOT_A_NUMBER);
        return Double.isNaN(value) ? null : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 缓存有效期判定。cache-minutes 配成 0 或负数视为不缓存，方便调试时看实时上游返回。
     *
     * @param entry 缓存条目
     * @return true 表示仍在有效期内
     */
    private boolean isFresh(CacheEntry entry) {
        int cacheMinutes = appProperties.getWeather().getCacheMinutes();
        if (cacheMinutes <= 0) {
            return false;
        }
        return System.currentTimeMillis() - entry.timestampMillis <= Duration.ofMinutes(cacheMinutes).toMillis();
    }

    /**
     * 写入缓存前先腾位置：过期项优先清掉，都新鲜就整体清空。
     * 单用户场景几乎触发不到，只为不让缓存无上限生长。
     *
     * @param city 城市名
     * @param weather 天气数据
     */
    private void putCache(String city, WeatherVO weather) {
        if (cache.size() >= MAX_CACHE_CITIES) {
            cache.entrySet().removeIf(entry -> !isFresh(entry.getValue()));
            if (cache.size() >= MAX_CACHE_CITIES) {
                cache.clear();
            }
        }
        cache.put(city, new CacheEntry(weather, System.currentTimeMillis()));
    }

    /**
     * 缓存条目：数据 + 写入时刻。做成不可变的私有内部类，避免外部误改时间戳把过期数据续命。
     */
    private static final class CacheEntry {

        private final WeatherVO weather;

        private final long timestampMillis;

        private CacheEntry(WeatherVO weather, long timestampMillis) {
            this.weather = weather;
            this.timestampMillis = timestampMillis;
        }
    }

    /**
     * WMO 码的中文描述与图标标识成对出现，用 record 绑在一起，防止两个映射各改一半。
     *
     * @param text 中文描述
     * @param icon 图标标识
     */
    private record WmoWeather(String text, String icon) {
    }
}
