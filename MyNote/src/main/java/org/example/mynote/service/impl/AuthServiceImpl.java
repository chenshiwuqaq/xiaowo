package org.example.mynote.service.impl;

import org.example.mynote.common.BizException;
import org.example.mynote.common.ResultCode;
import org.example.mynote.dao.UserDAO;
import org.example.mynote.dto.LoginDTO;
import org.example.mynote.dto.RegisterDTO;
import org.example.mynote.dto.ResetPasswordDTO;
import org.example.mynote.entity.UserDO;
import org.example.mynote.service.AuthService;
import org.example.mynote.util.JwtUtil;
import org.example.mynote.vo.LoginVO;
import org.example.mynote.vo.ProviderVO;
import org.example.mynote.vo.UserVO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 账号鉴权服务实现。
 *
 * <p>该类是匿名可达的业务入口，凡是"可能泄露用户存在与否"的分支都做了口径统一：
 * 账号不存在与密码错误返回完全相同的 LOGIN_FAILED。密码全程 BCrypt 散列，
 * 默认设置（主题色、目标字数等）写在服务端常量里，保证新用户开箱即用且口径一致。</p>
 *
 * @author MyNote
 */
@Service
public class AuthServiceImpl implements AuthService {

    /**
     * 新用户默认主题强调色，与前端设计稿一致
     */
    private static final String DEFAULT_THEME_COLOR = "#C06A46";

    /**
     * 新用户默认主题模式
     */
    private static final String DEFAULT_THEME_MODE = "light";

    /**
     * 新用户默认笔记背景
     */
    private static final String DEFAULT_NOTE_BG = "paper";

    /**
     * 新用户默认天气城市
     */
    private static final String DEFAULT_WEATHER_CITY = "杭州";

    /**
     * 新用户默认每日字数目标
     */
    private static final int DEFAULT_DAILY_GOAL = 1000;

    /**
     * 新用户默认正文字号
     */
    private static final int DEFAULT_FONT_SIZE = 16;

    /**
     * 账号状态：正常
     */
    private static final Integer STATUS_NORMAL = 1;

    /**
     * 账号状态：禁用
     */
    private static final Integer STATUS_DISABLED = 0;

    private final UserDAO userDAO;

    private final PasswordEncoder passwordEncoder;

    private final JwtUtil jwtUtil;

    public AuthServiceImpl(UserDAO userDAO, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userDAO = userDAO;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    /**
     * 注册新用户：查重后落库并立即签发令牌。
     *
     * <p>查重用 existsByAccount 而不是先查再判空，只为少传一个实体；默认设置在此集中写入，
     * 前端注册表单不必（也不允许）携带这些字段。密码 encode 后入库，明文不落任何存储。</p>
     *
     * @param dto 注册信息
     * @return 令牌与新用户资料
     */
    @Override
    public LoginVO register(RegisterDTO dto) {
        if (userDAO.existsByAccount(dto.getAccount())) {
            throw new BizException(ResultCode.ACCOUNT_EXISTS);
        }
        LocalDateTime now = LocalDateTime.now();
        UserDO user = new UserDO();
        user.setAccount(dto.getAccount());
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setEmail(dto.getEmail());
        user.setThemeColor(DEFAULT_THEME_COLOR);
        user.setThemeMode(DEFAULT_THEME_MODE);
        user.setNoteBg(DEFAULT_NOTE_BG);
        user.setWeatherCity(DEFAULT_WEATHER_CITY);
        user.setDailyGoal(DEFAULT_DAILY_GOAL);
        user.setFontSize(DEFAULT_FONT_SIZE);
        user.setStatus(STATUS_NORMAL);
        user.setCreateTime(now);
        user.setUpdateTime(now);
        UserDO saved = userDAO.save(user);
        return buildLoginVO(saved);
    }

    /**
     * 登录：账号不存在与密码错误走同一个 LOGIN_FAILED，提示完全一致。
     *
     * <p>这是有意为之——两种失败若返回不同文案，等于给攻击者一个免费的"账号是否存在"探测接口。
     * status=0 的禁用提示放在口令验证之后，避免"禁用账号"本身又成为存在性探针。</p>
     *
     * @param dto 登录信息
     * @return 令牌与用户资料
     */
    @Override
    public LoginVO login(LoginDTO dto) {
        UserDO user = userDAO.findByAccount(dto.getAccount()).orElse(null);
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BizException(ResultCode.LOGIN_FAILED);
        }
        if (STATUS_DISABLED.equals(user.getStatus())) {
            throw new BizException("账号已被禁用，请联系管理员处理");
        }
        return buildLoginVO(user);
    }

    /**
     * 自助重置密码：账号与邮箱联合命中才允许写入新密文。
     *
     * <p>找不到即 RESET_FAILED，不区分"账号不存在"与"邮箱不匹配"，理由同登录防探测。
     * 改完不签发令牌：重置是一个独立动作，若还持有旧口令应正常登录，
     * 直接发令牌会让"仅凭账号+邮箱"升级为完整会话，安全语义不对等。</p>
     *
     * @param dto 账号、绑定邮箱与新密码
     */
    @Override
    public void resetPassword(ResetPasswordDTO dto) {
        UserDO user = userDAO.findFirstByAccountAndEmail(dto.getAccount(), dto.getEmail())
                .orElseThrow(() -> new BizException(ResultCode.RESET_FAILED));
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        user.setUpdateTime(LocalDateTime.now());
        userDAO.save(user);
    }

    /**
     * 探测账号可否注册：未被占用返回 true。
     *
     * <p>空账号直接返回 false 而不是抛错——这是输入框失焦时的轻量探测，
     * 弹"账号不能为空"会打断输入体验，返回"不可用"即可。</p>
     *
     * @param account 待探测账号
     * @return true 表示可注册
     */
    @Override
    public Boolean checkAccount(String account) {
        if (account == null || account.trim().isEmpty()) {
            return false;
        }
        return !userDAO.existsByAccount(account.trim());
    }

    /**
     * 返回预置 AI 服务商列表。
     *
     * <p>硬编码在服务端而非配置文件：服务商与默认模型/地址是产品内置知识，
     * 改代码即改文档，不指望用户去动 yml。每次构造新列表返回，避免共享可变对象被调用方篡改。
     * custom 只是占位模板，baseUrl 填示例地址，用户保存配置时会替换成自己的。</p>
     *
     * @return 服务商列表
     */
    @Override
    public List<ProviderVO> listProviders() {
        List<ProviderVO> providers = new ArrayList<>(6);
        providers.add(buildProvider("deepseek", "DeepSeek", "https://api.deepseek.com/v1",
                List.of("deepseek-chat", "deepseek-reasoner")));
        providers.add(buildProvider("openai", "OpenAI", "https://api.openai.com/v1",
                List.of("gpt-4o-mini", "gpt-4o")));
        providers.add(buildProvider("qianwen", "通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1",
                List.of("qwen-plus", "qwen-turbo")));
        providers.add(buildProvider("kimi", "Kimi", "https://api.moonshot.cn/v1",
                List.of("moonshot-v1-8k")));
        providers.add(buildProvider("zhipu", "智谱 GLM", "https://open.bigmodel.cn/api/paas/v4",
                List.of("glm-4-flash")));
        providers.add(buildProvider("custom", "自定义服务商", "https://api.example.com/v1",
                List.of("自定义")));
        return providers;
    }

    /**
     * 组装"令牌 + 用户资料"返回体，注册与登录共用同一条出口逻辑。
     *
     * @param user 已落库的用户实体
     * @return 登录结果
     */
    private LoginVO buildLoginVO(UserDO user) {
        LoginVO vo = new LoginVO();
        vo.setToken(jwtUtil.create(user.getId(), user.getAccount()));
        vo.setUser(toVO(user));
        return vo;
    }

    /**
     * 构造单个服务商对象，收敛重复的四段式赋值。
     *
     * @param code    服务商编码
     * @param name    展示名称
     * @param baseUrl 默认接口地址
     * @param models  预置模型列表
     * @return 服务商视图对象
     */
    private ProviderVO buildProvider(String code, String name, String baseUrl, List<String> models) {
        ProviderVO vo = new ProviderVO();
        vo.setCode(code);
        vo.setName(name);
        vo.setBaseUrl(baseUrl);
        vo.setModels(models);
        return vo;
    }

    /**
     * 实体转视图对象：逐字段手工赋值，password、status、updateTime 等内部字段永不外泄。
     *
     * <p>不用 BeanUtils 之类反射拷贝：显式赋值能一眼看清哪些字段出了门，
     * 实体日后加敏感字段也不会被"顺手拷出去"。</p>
     *
     * @param user 用户实体
     * @return 用户视图对象
     */
    private UserVO toVO(UserDO user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setAccount(user.getAccount());
        vo.setUsername(user.getUsername());
        vo.setEmail(user.getEmail());
        vo.setAvatar(user.getAvatar());
        vo.setIntro(user.getIntro());
        vo.setThemeColor(user.getThemeColor());
        vo.setThemeMode(user.getThemeMode());
        vo.setNoteBg(user.getNoteBg());
        vo.setWeatherCity(user.getWeatherCity());
        vo.setDailyGoal(user.getDailyGoal());
        vo.setFontSize(user.getFontSize());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }
}
