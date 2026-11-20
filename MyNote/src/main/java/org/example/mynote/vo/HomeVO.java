package org.example.mynote.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 主页看板总览，一次请求把"推开房门看到的全部信息"带给前端。
 *
 * <p>做成一个大对象而不是拆成七个接口，是因为主页要在首屏一次渲染完；多轮请求在本地网络
 * 也可能变成几百毫秒的白屏。列表类字段全部给空数组而不是 null，前端可以无脑 map。</p>
 *
 * <p><b>两个字数口径必须分清（这是设计，不是 bug）</b>：</p>
 * <ul>
 *   <li>{@code totalWords} 作品总字数：当前库里未删除笔记的 word_count 之和，
 *       删掉一篇笔记它就会变小，代表"我现在有多少作品"；</li>
 *   <li>{@code writtenWords} 书写总量：全部书写日志 word_delta 之和，含写下后又删改掉的字数，
 *       只增不减，代表"我这些年敲了多少字"，所以它一定不小于 totalWords。</li>
 * </ul>
 *
 * @author MyNote
 */
@Data
public class HomeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 昵称，用于开场白
     */
    private String username;

    /**
     * 头像相对路径，为 null 时前端用首字母占位
     */
    private String avatar;

    /**
     * 主题主色
     */
    private String themeColor;

    /**
     * 未删除的笔记篇数
     */
    private Integer noteCount;

    /**
     * 口径一：现存作品总字数
     */
    private Long totalWords;

    /**
     * 口径二：历史书写总字数（含删改）
     */
    private Long writtenWords;

    /**
     * 今日书写净增字数
     */
    private Integer todayWords;

    /**
     * 用户设定的每日目标字数
     */
    private Integer dailyGoal;

    /**
     * 今日是否达标：todayWords >= dailyGoal
     */
    private Boolean goalReached;

    /**
     * 连续打卡天数，断档后为重新累积的天数；今天没写但昨天写了不算断，
     * 昨天及更早都断了才是 0
     */
    private Integer streakDays;

    /**
     * 当前连续天数命中的里程碑文案，未到 7 天时为 null
     */
    private String lastMilestone;

    /**
     * 各类型笔记篇数
     */
    private List<TypeCountVO> typeDistribution;

    /**
     * 最近在写的笔记（按更新时间倒序，取前 6 条）
     */
    private List<NoteBriefVO> recentNotes;

    /**
     * 置顶笔记
     */
    private List<NoteBriefVO> topNotes;

    /**
     * 近 120 天书写热力图数据，缺字数的日期已补 0
     */
    private List<DailyWordVO> wordCalendar;

    /**
     * 近 30 天字数趋势，缺字数的日期已补 0
     */
    private List<DailyWordVO> trend;

    /**
     * 天气卡片；上游不可用或用户未设置城市时为 null，此时主页其余字段照常返回
     */
    private WeatherVO weather;
}
