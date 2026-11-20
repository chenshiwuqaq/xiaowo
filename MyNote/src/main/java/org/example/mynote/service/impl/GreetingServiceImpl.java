package org.example.mynote.service.impl;

import org.example.mynote.common.BizException;
import org.example.mynote.common.ResultCode;
import org.example.mynote.common.UserContext;
import org.example.mynote.dao.UserDAO;
import org.example.mynote.entity.UserDO;
import org.example.mynote.service.GreetingService;
import org.example.mynote.service.StatsService;
import org.example.mynote.vo.GreetingVO;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 开场白实现：按时段说不同的话，再随手递两张小贴士。
 *
 * <p>问候是产品气质所在，所以文案集中在这里维护，前端只负责摆出来；时段边界写成常量，
 * 早午晚怎么切不再靠散落的 if 猜。小贴士每次随机两条而不是固定一句，
 * 是为了让"回到小窝"这件事每次都有点新东西，又不至于像通知栏那样唠叨。</p>
 *
 * <p>达标鼓励只在真达标时说，不做"加油你还差 380 字"这类催促——数字压力留给统计卡片，
 * 开场白负责让人愿意坐下来。</p>
 *
 * @author MyNote
 */
@Service
public class GreetingServiceImpl implements GreetingService {

    /**
     * 每次给出的小贴士条数：一句太多、一句太少，两句够挑但不聒噪
     */
    private static final int TIP_COUNT = 2;

    /**
     * 小贴士文案池
     */
    private static final String[] TIPS = {
            "从昨天没写完的那句话开始，比从零开头容易十倍。",
            "只写五分钟。时间一到，你可以名正言顺地停下。",
            "写乱了也别删，先摊开，回头再收。",
            "把今天最在意的一件小事记下来，一年后它会变成大事。",
            "读一遍上周的段落，你会知道自己在往哪儿走。",
            "给卡住的那段先写个最差版本，它往往会被你自己改好。",
            "记下别人刚刚说的那句话，比自己的形容词鲜活。",
            "换个标题试试，一篇笔记常常在新名字里重新开口。",
            "给这篇配一张图吧，眼睛会替脑子想起内容。",
            "窗开一条缝，风会帮你把思路吹松一点。",
    };

    /**
     * 达标后追加的鼓励语
     */
    private static final String GOAL_REACHED_TIP = "今天的份额已经攒够，之后落下的字都算白赚的。";

    /**
     * 昵称缺失时的称呼，不给"null，早上好"这种话
     */
    private static final String DEFAULT_ADDRESS = "你";

    /**
     * 副标题日期格式，年月日与星期另拼，避免把星期也塞进 pattern
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日");

    /**
     * 星期名，下标为 DayOfWeek.getValue() - 1
     */
    private static final String[] WEEK_DAY_NAMES = {"星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"};

    private static final String SUBTITLE_SEPARATOR = " · ";

    private static final String GREETING_JOINER = "，";

    private final UserDAO userDAO;

    private final StatsService statsService;

    public GreetingServiceImpl(UserDAO userDAO, StatsService statsService) {
        this.userDAO = userDAO;
        this.statsService = statsService;
    }

    @Override
    public GreetingVO greet() {
        Long userId = UserContext.require();
        // 与其它统计/天气服务口径一致：账号查不到就是登录态失效，不返回半截问候
        UserDO user = userDAO.findById(userId)
                .orElseThrow(() -> new BizException(ResultCode.UNAUTHORIZED, "登录状态已失效，请重新登录"));
        // 日期与时段取同一次时钟读数，跨零点时不会出现"按今天配昨天的话"
        LocalDateTime now = LocalDateTime.now();

        GreetingVO vo = new GreetingVO();
        vo.setGreeting(buildGreeting(user, now.getHour()));
        vo.setSubtitle(buildSubtitle(now.toLocalDate()));
        vo.setTips(buildTips());
        return vo;
    }

    /**
     * 昵称 + 时段问候。昵称为空时退回通用称呼，避免出现"，早上好"这种开头。
     *
     * @param user 当前用户
     * @param hour 当前小时，0–23
     * @return 问候语
     */
    private String buildGreeting(UserDO user, int hour) {
        String username = user.getUsername();
        String address = username == null || username.isBlank() ? DEFAULT_ADDRESS : username.trim();
        return address + GREETING_JOINER + DayPeriod.of(hour).text;
    }

    /**
     * 副标题形如"2026 年 10 月 4 日 · 星期日"。
     *
     * @param today 今天
     * @return 日期与星期
     */
    private String buildSubtitle(LocalDate today) {
        String weekDay = WEEK_DAY_NAMES[today.getDayOfWeek().getValue() - 1];
        return today.format(DATE_FORMATTER) + SUBTITLE_SEPARATOR + weekDay;
    }

    /**
     * 随机两句话，外加今日达标时的追加鼓励。
     *
     * <p>抽第二张时跳过第一张的下标，保证同一次请求不会出现两句一模一样的话——
     * 重复的鼓励看起来像坏掉的复读机。</p>
     *
     * @return 小贴士列表，长度为 2 或 3
     */
    private List<String> buildTips() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int first = random.nextInt(TIPS.length);
        int second = random.nextInt(TIPS.length - 1);
        if (second >= first) {
            second++;
        }
        List<String> tips = new ArrayList<>(TIP_COUNT + 1);
        tips.add(TIPS[first]);
        tips.add(TIPS[second]);
        if (statsService.todayGoalReached()) {
            tips.add(GOAL_REACHED_TIP);
        }
        return tips;
    }

    /**
     * 时段划分（左闭右开，按整点边界）：凌晨 0–5、早晨 5–8、上午 8–12、午后 12–18、
     * 傍晚 18–21、夜晚 21–23、深夜 23–24。声明顺序必须与边界递增一致，
     * {@link #of(int)} 靠这个顺序从前往后取最后一个命中的档位。
     */
    private enum DayPeriod {

        /**
         * 0–5 点
         */
        LATE_NIGHT(0, "凌晨了，安静得能听见笔尖，想到什么就写两句"),

        /**
         * 5–8 点
         */
        MORNING_EARLY(5, "早啊，脑子还空着，正好放下今天第一个字"),

        /**
         * 8–12 点
         */
        MORNING(8, "上午精神最足，把最难啃的那段挪到现在"),

        /**
         * 12–18 点
         */
        AFTERNOON(12, "午后有点困，写不动就抄两句喜欢的句子"),

        /**
         * 18–21 点
         */
        EVENING(18, "傍晚的风刚好，适合给今天的故事收个尾"),

        /**
         * 21–23 点
         */
        NIGHT(21, "晚上好，白天的事可以放下了，这一页只剩你"),

        /**
         * 23–24 点
         */
        MIDNIGHT(23, "夜深了，别硬撑着写完，剩下的明天接着说");

        /**
         * 该时段的起始整点
         */
        private final int startHour;

        /**
         * 问候文案
         */
        private final String text;

        DayPeriod(int startHour, String text) {
            this.startHour = startHour;
            this.text = text;
        }

        /**
         * 按小时定位时段。
         *
         * @param hour 0–23
         * @return 时段
         */
        static DayPeriod of(int hour) {
            DayPeriod matched = LATE_NIGHT;
            for (DayPeriod period : values()) {
                if (hour >= period.startHour) {
                    matched = period;
                }
            }
            return matched;
        }
    }
}
