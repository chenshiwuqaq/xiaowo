package org.example.mynote.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 某一天的书写字数，热力图与趋势图共用的最小数据点。
 *
 * <p>date 之所以输出成 yyyy-MM-dd 字符串而不是 LocalDate：一是前端要按它逐格铺日历，
 * 字符串可直接当下标用；二是 application.yml 里的 spring.jackson.date-format 只作用于
 * java.util.Date，对 JSR-310 类型不生效，改成字符串后契约里的日期格式就不会随序列化配置漂移。</p>
 *
 * <p>words 取书写日志在当天的净增量之和（wordDelta 可正可负）。没有书写记录的日期补 0，
 * 这样热力图才能拿到连续、等长的格子序列；但"某天删改比新增多"会算出负数，
 * 这属于真实数据，不做截断，前端渲染色阶时自行按不小于 0 处理即可。</p>
 *
 * @author MyNote
 */
@Data
public class DailyWordVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 日期，格式 yyyy-MM-dd
     */
    private String date;

    /**
     * 当天书写净增字数
     */
    private Integer words;
}
