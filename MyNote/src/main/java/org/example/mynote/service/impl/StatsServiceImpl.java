package org.example.mynote.service.impl;

import org.example.mynote.common.BizException;
import org.example.mynote.common.Constants;
import org.example.mynote.common.ResultCode;
import org.example.mynote.common.UserContext;
import org.example.mynote.dao.NoteDAO;
import org.example.mynote.dao.UserDAO;
import org.example.mynote.dao.WritingLogDAO;
import org.example.mynote.entity.UserDO;
import org.example.mynote.entity.WritingLogDO;
import org.example.mynote.service.StatsService;
import org.example.mynote.vo.DailyWordVO;
import org.example.mynote.vo.HomeVO;
import org.example.mynote.vo.NoteBriefVO;
import org.example.mynote.vo.TrendVO;
import org.example.mynote.vo.TypeCountVO;
import org.example.mynote.vo.WritingCalendarVO;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 书写统计实现。
 *
 * <p><b>口径说明（为什么两个字数不一样）</b>：</p>
 * <ul>
 *   <li>totalWords 走 NoteDAO.countAndSumWords，只统计 deleted=false 的笔记的 word_count，
 *       是"作品存量"，删笔记会变小；</li>
 *   <li>writtenWords 走 WritingLogDAO.sumAll，把历史上每一次保存产生的 word_delta 全加起来，
 *       写下又删掉的字照样计入，是"劳动量"，只增不减。 writtenWords 比 totalWords 大是设计意图，
 *       用它安慰作者"你其实已经写了这么多"，不能被存量数字抹掉。</li>
 * </ul>
 *
 * <p><b>查询次数</b>：noteCount、typeDistribution、topNotes 全部由一次 findAllBrief 在内存里算出，
 * 不再对同一张表打三趟聚合；热力图与趋势图共用一次区间日志查询（趋势窗口是热力图窗口的子集）。
 * 只有 todayWords 用独立聚合，因为它要的是"此刻"的值，随写随变，缓存它反而会说谎。</p>
 *
 * @author MyNote
 */
@Service
public class StatsServiceImpl implements StatsService {

    /**
     * 主页"最近在写"卡片条数：首屏两行三列刚好放满，再多就挤压热力图的位置
     */
    private static final int RECENT_NOTE_LIMIT = 6;

    /**
     * 主页热力图默认窗口，约 17 周，与前端日历格子数一致
     */
    private static final int OVERVIEW_CALENDAR_DAYS = 120;

    /**
     * 主页趋势图默认窗口，一个月
     */
    private static final int OVERVIEW_TREND_DAYS = 30;

    /**
     * 统计窗口下限，0 天会让前端拿到空数组去算色阶
     */
    private static final int MIN_STAT_DAYS = 1;

    /**
     * 统计窗口上限：一年，超过这个跨度的请求一律视为误传
     */
    private static final int MAX_STAT_DAYS = 366;

    /**
     * 每日目标兜底值，与 mn_user.daily_goal 的建表默认值保持一致。
     * 取 0 会让"今日已达标"永远为真，比报错更糟
     */
    private static final int DEFAULT_DAILY_GOAL = 1000;

    /**
     * 连续打卡里程碑，降序排列以便命中即返回（取当前连续天数命中的最大档）
     */
    private static final int[] STREAK_MILESTONES = {365, 100, 30, 7};

    /**
     * 里程碑文案，键为 {@link #STREAK_MILESTONES} 中的天数档位
     */
    private static final Map<Integer, String> STREAK_MILESTONE_TEXTS = Map.of(
            7, "连续 7 天，手感刚热起来",
            30, "连续 30 天，笔没停过",
            100, "连续 100 天，写作已经是身体的一部分",
            365, "连续 365 天，一年都没放下笔");

    /**
     * 类型码 → 中文名。用有序的不可变映射：主页扇区按它出图（顺序稳定），
     * 卡片取名字也用它，两处共用一份，不会出现"分布图有随笔、卡片说其他"
     */
    private static final Map<Integer, String> NOTE_TYPE_NAMES;

    static {
        Map<Integer, String> names = new LinkedHashMap<>(4);
        names.put(Constants.NOTE_TYPE_RESEARCH, "科研");
        names.put(Constants.NOTE_TYPE_LIFE, "生活");
        names.put(Constants.NOTE_TYPE_FICTION, "小说");
        names.put(Constants.NOTE_TYPE_ESSAY, "随笔");
        NOTE_TYPE_NAMES = Collections.unmodifiableMap(names);
    }

    /**
     * 脏数据或将来新增类型的归集码，保证各扇区数量之和等于 noteCount
     */
    private static final int OTHER_NOTE_TYPE = 0;

    private static final String OTHER_NOTE_TYPE_NAME = "其他";

    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final int ZERO_WORDS = 0;

    /**
     * 聚合用 Map 的初始容量下限，日志条数为 0 或 1 时也不用极小容量去反复扩容
     */
    private static final int MIN_MAP_CAPACITY = 16;

    private final UserDAO userDAO;

    private final NoteDAO noteDAO;

    private final WritingLogDAO writingLogDAO;

    public StatsServiceImpl(UserDAO userDAO, NoteDAO noteDAO, WritingLogDAO writingLogDAO) {
        this.userDAO = userDAO;
        this.noteDAO = noteDAO;
        this.writingLogDAO = writingLogDAO;
    }

    /**
     * {@inheritDoc}
     *
     * <p>实现顺序按"先算便宜的内存聚合、再发独立聚合查询"排：一次 findAllBrief 摊平出
     * 篇数、类型分布与置顶列表，字数存量与书写总量各自走一条 SUM 语句，不进 Java 层循环。</p>
     */
    @Override
    public HomeVO overview() {
        Long userId = UserContext.require();
        UserDO user = requireUser(userId);
        List<NoteDAO.NoteBrief> briefs = noteDAO.findAllBrief(userId);
        LocalDate today = LocalDate.now();

        HomeVO vo = new HomeVO();
        vo.setUsername(user.getUsername());
        vo.setAvatar(user.getAvatar());
        vo.setThemeColor(user.getThemeColor());
        vo.setNoteCount(briefs.size());
        vo.setTotalWords(sumCurrentWords(userId));
        vo.setWrittenWords(writingLogDAO.sumAll(userId));

        int todayWords = writingLogDAO.sumOfDay(userId, today);
        int dailyGoal = resolveDailyGoal(user);
        vo.setTodayWords(todayWords);
        vo.setDailyGoal(dailyGoal);
        vo.setGoalReached(todayWords >= dailyGoal);

        int streakDays = calcStreakDays(userId, today);
        vo.setStreakDays(streakDays);
        vo.setLastMilestone(resolveMilestoneText(streakDays));

        vo.setTypeDistribution(buildTypeDistribution(briefs));
        vo.setTopNotes(toBriefVOList(filterTopNotes(briefs)));
        vo.setRecentNotes(toBriefVOList(
                noteDAO.findRecent(userId, PageRequest.of(0, RECENT_NOTE_LIMIT))));

        // 趋势窗口是热力图窗口的尾部，两次输出只查一次日志
        LocalDate calendarStart = today.minusDays(OVERVIEW_CALENDAR_DAYS - 1L);
        Map<LocalDate, Integer> dailyWords = loadDailyWords(calendarStart, today, userId);
        vo.setWordCalendar(toDailyWords(dailyWords, calendarStart, today));
        vo.setTrend(toDailyWords(dailyWords, today.minusDays(OVERVIEW_TREND_DAYS - 1L), today));
        return vo;
    }

    @Override
    public WritingCalendarVO writingCalendar(int days) {
        Long userId = UserContext.require();
        int span = clampDays(days);
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(span - 1L);

        WritingCalendarVO vo = new WritingCalendarVO();
        vo.setDays(toDailyWords(loadDailyWords(start, end, userId), start, end));
        return vo;
    }

    @Override
    public TrendVO trend(int days) {
        Long userId = UserContext.require();
        int span = clampDays(days);
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(span - 1L);

        List<DailyWordVO> series = toDailyWords(loadDailyWords(start, end, userId), start, end);
        long total = 0L;
        int max = ZERO_WORDS;
        for (DailyWordVO point : series) {
            int words = point.getWords() == null ? ZERO_WORDS : point.getWords();
            total += words;
            // 全是删改的日子会给出负值，纵轴上界不能被它带成负数
            max = Math.max(max, words);
        }
        TrendVO vo = new TrendVO();
        vo.setDays(series);
        vo.setTotal(total);
        vo.setMax(max);
        return vo;
    }

    @Override
    public boolean todayGoalReached() {
        Long userId = UserContext.require();
        UserDO user = requireUser(userId);
        return writingLogDAO.sumOfDay(userId, LocalDate.now()) >= resolveDailyGoal(user);
    }

    /**
     * 取当前登录用户，取不到说明令牌对应的账号已不存在（换库、清库都可能遇到）。
     * 这里按未登录处理，让前端跳回登录页，而不是返回半截数据。
     *
     * @param userId 当前用户 id
     * @return 用户
     */
    private UserDO requireUser(Long userId) {
        return userDAO.findById(userId)
                .orElseThrow(() -> new BizException(ResultCode.UNAUTHORIZED, "登录状态已失效，请重新登录"));
    }

    /**
     * 存量字数：未删除笔记的 word_count 之和。
     *
     * <p>countAndSumWords 的第一列是篇数，这里不用它——noteCount 已经从 findAllBrief 得到，
     * 两个来源迟早会打架，留一个为准。求和列按 Number 取 long，SUM 在 MySQL 驱动里可能返回
     * Long 也可能返回 BigDecimal，直接强转 Long 会在某次方言变更后炸掉。</p>
     *
     * @param userId 当前用户 id
     * @return 存量字数
     */
    private long sumCurrentWords(Long userId) {
        List<Object[]> rows = noteDAO.countAndSumWords(userId);
        if (rows.isEmpty()) {
            return 0L;
        }
        Object sum = rows.get(0)[1];
        return sum == null ? 0L : ((Number) sum).longValue();
    }

    /**
     * 目标字数的容错读取：老行可能留空，用建表默认值补，不能当成 0。
     *
     * @param user 用户
     * @return 每日目标字数
     */
    private int resolveDailyGoal(UserDO user) {
        Integer goal = user.getDailyGoal();
        if (goal == null || goal < 0) {
            return DEFAULT_DAILY_GOAL;
        }
        return goal;
    }

    /**
     * 类型分布。
     *
     * <p>四个已知类型即使一篇都没有也要输出（值为 0），否则环形图的图例与配色会随数据缺失跳动；
     * 未知编码与空值统一归到"其他"，保证各扇区之和等于 noteCount，前端无需再校验守恒。</p>
     *
     * @param briefs 一次 findAllBrief 的结果
     * @return 按类型码 1→4 排列的分布，必要时尾部追加"其他"
     */
    private List<TypeCountVO> buildTypeDistribution(List<NoteDAO.NoteBrief> briefs) {
        Map<Integer, Long> counted = new HashMap<>(NOTE_TYPE_NAMES.size());
        long otherCount = 0L;
        for (NoteDAO.NoteBrief brief : briefs) {
            Integer type = brief.getNoteType();
            if (type == null || !NOTE_TYPE_NAMES.containsKey(type)) {
                otherCount++;
                continue;
            }
            counted.merge(type, 1L, Long::sum);
        }

        List<TypeCountVO> distribution = new ArrayList<>(NOTE_TYPE_NAMES.size() + 1);
        for (Map.Entry<Integer, String> entry : NOTE_TYPE_NAMES.entrySet()) {
            distribution.add(buildTypeCount(entry.getKey(), entry.getValue(),
                    counted.getOrDefault(entry.getKey(), 0L)));
        }
        if (otherCount > 0) {
            distribution.add(buildTypeCount(OTHER_NOTE_TYPE, OTHER_NOTE_TYPE_NAME, otherCount));
        }
        return distribution;
    }

    private TypeCountVO buildTypeCount(Integer noteType, String name, Long count) {
        TypeCountVO vo = new TypeCountVO();
        vo.setNoteType(noteType);
        vo.setName(name);
        vo.setCount(count);
        return vo;
    }

    /**
     * 置顶笔记：投影里的 top 可能为 null（历史行没写这个列），只有明确为 true 才算置顶。
     *
     * @param briefs 全部有效笔记
     * @return 置顶列表，沿用 findAllBrief 的更新时间倒序
     */
    private List<NoteDAO.NoteBrief> filterTopNotes(List<NoteDAO.NoteBrief> briefs) {
        List<NoteDAO.NoteBrief> topNotes = new ArrayList<>();
        for (NoteDAO.NoteBrief brief : briefs) {
            if (Boolean.TRUE.equals(brief.getTop())) {
                topNotes.add(brief);
            }
        }
        return topNotes;
    }

    /**
     * 连续打卡天数。
     *
     * <p>数据源 distinctWritingDates 是倒序且只包含 word_delta &gt; 0 的日子，所以"今天写了一堆
     * 又全删干净"不算打卡——激励的是留下东西的人，不是反复按保存的人。</p>
     *
     * <p>起点判定放宽到昨天：白天没写不等于断签，若最近一次书写就在昨天，连续仍然成立，
     * 用户不必在零点前赶着提交才算数。最近一次早于昨天，或今天之前就没有过书写，则为 0。</p>
     *
     * @param userId 当前用户 id
     * @param today  今天
     * @return 连续天数
     */
    private int calcStreakDays(Long userId, LocalDate today) {
        List<LocalDate> dates = writingLogDAO.distinctWritingDates(userId);
        if (dates.isEmpty()) {
            return 0;
        }
        LocalDate latest = dates.get(0);
        // 最近一次书写既不是今天也不是昨天，说明中间断过，连续清零
        if (!latest.isEqual(today) && !latest.isEqual(today.minusDays(1L))) {
            return 0;
        }
        int streak = 1;
        LocalDate previous = latest;
        for (int i = 1; i < dates.size(); i++) {
            LocalDate current = dates.get(i);
            if (!previous.minusDays(1L).isEqual(current)) {
                // 倒序序列里一旦相邻两天不再是前后脚，后面必然更旧，无需继续
                break;
            }
            streak++;
            previous = current;
        }
        return streak;
    }

    /**
     * 里程碑文案：取当前连续天数命中的最大档位，未到 7 天返回 null（不硬凑表扬）。
     *
     * @param streakDays 连续天数
     * @return 文案或 null
     */
    private String resolveMilestoneText(int streakDays) {
        for (int milestone : STREAK_MILESTONES) {
            if (streakDays >= milestone) {
                return STREAK_MILESTONE_TEXTS.get(milestone);
            }
        }
        return null;
    }

    /**
     * 请求天数的钳制：越界按边界取值而不是抛错。主页参数来自前端组件，一个 0 或 9999
     * 只该被纠正，不该让首屏拿到错误码。
     *
     * @param days 请求天数
     * @return 1~366 之间的天数
     */
    private int clampDays(int days) {
        if (days < MIN_STAT_DAYS) {
            return MIN_STAT_DAYS;
        }
        return Math.min(days, MAX_STAT_DAYS);
    }

    /**
     * 区间内的书写日志按天聚合成"日期 → 净增字数"，同一天多次保存会累加。
     *
     * @param start  起始日期（含）
     * @param end    结束日期（含）
     * @param userId 当前用户 id
     * @return 日期到字数的映射，没有记录的天不在表里
     */
    private Map<LocalDate, Integer> loadDailyWords(LocalDate start, LocalDate end, Long userId) {
        List<WritingLogDO> logs = writingLogDAO.findByUserIdAndLogDateBetweenOrderByLogDateAsc(userId, start, end);
        Map<LocalDate, Integer> daily = new HashMap<>(Math.max(logs.size(), MIN_MAP_CAPACITY));
        for (WritingLogDO writingLog : logs) {
            if (writingLog.getLogDate() == null) {
                continue;
            }
            int delta = writingLog.getWordDelta() == null ? ZERO_WORDS : writingLog.getWordDelta();
            daily.merge(writingLog.getLogDate(), delta, Integer::sum);
        }
        return daily;
    }

    /**
     * 把聚合结果铺成连续日期序列，没写字的天补 0。
     *
     * <p>前端热力图是按数组下标铺格子的，缺一天就会整体错位，所以这里必须补齐而不是让前端自己找。</p>
     *
     * @param daily 聚合结果
     * @param start 起始日期（含）
     * @param end   结束日期（含）
     * @return 长度等于区间天数的序列，按日期正序
     */
    private List<DailyWordVO> toDailyWords(Map<LocalDate, Integer> daily, LocalDate start, LocalDate end) {
        List<DailyWordVO> series = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            DailyWordVO point = new DailyWordVO();
            point.setDate(date.format(DAY_FORMATTER));
            point.setWords(daily.getOrDefault(date, ZERO_WORDS));
            series.add(point);
        }
        return series;
    }

    /**
     * 投影转 VO，顺带把类型中文名与时间格式补齐。
     *
     * @param briefs DAO 投影列表
     * @return VO 列表，入参为空时返回空列表
     */
    private List<NoteBriefVO> toBriefVOList(List<NoteDAO.NoteBrief> briefs) {
        List<NoteBriefVO> list = new ArrayList<>(briefs.size());
        for (NoteDAO.NoteBrief brief : briefs) {
            list.add(toBriefVO(brief));
        }
        return list;
    }

    private NoteBriefVO toBriefVO(NoteDAO.NoteBrief brief) {
        NoteBriefVO vo = new NoteBriefVO();
        vo.setId(brief.getId());
        vo.setTitle(brief.getTitle());
        vo.setSummary(brief.getSummary());
        vo.setFolderId(brief.getFolderId());
        vo.setNoteType(brief.getNoteType());
        vo.setNoteTypeName(NOTE_TYPE_NAMES.getOrDefault(brief.getNoteType(), OTHER_NOTE_TYPE_NAME));
        vo.setWordCount(brief.getWordCount() == null ? ZERO_WORDS : brief.getWordCount());
        vo.setTop(Boolean.TRUE.equals(brief.getTop()));
        vo.setCreateTime(formatDateTime(brief.getCreateTime()));
        vo.setUpdateTime(formatDateTime(brief.getUpdateTime()));
        return vo;
    }

    /**
     * 时间为空时返回 null（字段会被 Jackson 的 non_null 规则省略），不输出 "null" 字符串。
     *
     * @param time 时间
     * @return 格式化结果
     */
    private String formatDateTime(LocalDateTime time) {
        return time == null ? null : time.format(DATE_TIME_FORMATTER);
    }
}
