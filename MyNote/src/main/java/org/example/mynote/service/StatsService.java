package org.example.mynote.service;

import org.example.mynote.vo.HomeVO;
import org.example.mynote.vo.TrendVO;
import org.example.mynote.vo.WritingCalendarVO;

/**
 * 书写统计服务：主页看板、热力图与趋势图的数据来源。
 *
 * <p>统计一律以 {@code UserContext.require()} 的当前登录用户为过滤条件，接口不接收 userId 参数，
 * 从签名上断掉"前端传谁就查谁"的越权路径。</p>
 *
 * <p>本服务只算与库打交道的数据，不碰天气这类外部网络，保证统计接口不会因为断网而失败。</p>
 *
 * @author MyNote
 */
public interface StatsService {

    /**
     * 组装主页总览，除天气外的所有字段在此填好。
     *
     * <p>weather 字段留空由控制器注入：天气要出网，把它放在统计服务里会让"算字数"这件事
     * 背上网络失败的后果，分层后统计永远稳定，天气坏了自己置空。</p>
     *
     * @return 主页看板数据，各列表字段非 null
     */
    HomeVO overview();

    /**
     * 书写热力图数据。
     *
     * @param days 统计天数，以今天为结束日往前推；越界值按 1~366 钳制而不是抛错，
     *             前端传错一个数字不该让首屏空掉
     * @return 连续等长的日期序列，没写字的天补 0
     */
    WritingCalendarVO writingCalendar(int days);

    /**
     * 字数趋势数据。
     *
     * @param days 统计天数，钳制规则同上
     * @return 日期序列与合计、单日峰值
     */
    TrendVO trend(int days);

    /**
     * 今日字数是否已达每日目标，供开场白决定是否追加鼓励语。
     *
     * @return true 表示今日净增字数已达目标
     */
    boolean todayGoalReached();
}
