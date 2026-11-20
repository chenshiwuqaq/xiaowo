package org.example.mynote.service.impl;

import org.example.mynote.common.BizException;
import org.example.mynote.common.ResultCode;
import org.example.mynote.common.UserContext;
import org.example.mynote.dao.UserDAO;
import org.example.mynote.dto.PasswordUpdateDTO;
import org.example.mynote.dto.ProfileUpdateDTO;
import org.example.mynote.dto.SettingsDTO;
import org.example.mynote.entity.UserDO;
import org.example.mynote.service.UserService;
import org.example.mynote.vo.UserVO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 当前登录用户资料与设置服务实现。
 *
 * <p>每个方法都以 UserContext.require() 取 userId 并按 id 加载实体，更新时只回写 DTO 里
 * 声明过的字段——前端即便多传 account、status 也进不了实体，身份与权限完全由服务端掌握。</p>
 *
 * @author MyNote
 */
@Service
public class UserServiceImpl implements UserService {

    /**
     * 每日字数目标下限
     */
    private static final int DAILY_GOAL_MIN = 0;

    /**
     * 每日字数目标上限
     */
    private static final int DAILY_GOAL_MAX = 20000;

    /**
     * 正文字号下限
     */
    private static final int FONT_SIZE_MIN = 12;

    /**
     * 正文字号上限
     */
    private static final int FONT_SIZE_MAX = 24;

    private final UserDAO userDAO;

    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserDAO userDAO, PasswordEncoder passwordEncoder) {
        this.userDAO = userDAO;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 查询当前用户资料。
     *
     * <p>读的是 UserContext 里的人，不是请求参数里的人，令牌即身份。</p>
     *
     * @return 用户视图对象
     */
    @Override
    public UserVO getProfile() {
        return toVO(loadCurrentUser());
    }

    /**
     * 更新昵称、邮箱、签名与头像路径。
     *
     * <p>只回写这三个字段：account 不可变更由 DTO 与这里的赋值范围双重保证；
     * 邮箱格式已在 DTO 校验，此处信任校验结果不再重复判断。</p>
     *
     * @param dto 资料更新入参
     * @return 更新后的用户视图对象
     */
    @Override
    public UserVO updateProfile(ProfileUpdateDTO dto) {
        UserDO user = loadCurrentUser();
        user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());
        user.setIntro(dto.getIntro());
        // 头像单独走 /api/file/upload，路径回传到这里保存；传空表示"不改"而不是"清空"，
        // 否则前端少带一个字段就会把用户头像抹掉
        if (dto.getAvatar() != null && !dto.getAvatar().isBlank()) {
            user.setAvatar(dto.getAvatar().trim());
        }
        user.setUpdateTime(LocalDateTime.now());
        return toVO(userDAO.save(user));
    }

    /**
     * 更新外观与写作习惯设置，数值越界抛带区间的中文业务异常。
     *
     * <p>范围放在服务端而不是仅靠前端控件限制：接口可被直接调用，
     * 0~20000 / 12~24 是产品口径，超限写库会让主页进度条与编辑器排版失真。</p>
     *
     * @param dto 设置更新入参
     * @return 更新后的用户视图对象
     */
    @Override
    public UserVO updateSettings(SettingsDTO dto) {
        if (dto.getDailyGoal() < DAILY_GOAL_MIN || dto.getDailyGoal() > DAILY_GOAL_MAX) {
            throw new BizException("每日字数目标须在 " + DAILY_GOAL_MIN + "~" + DAILY_GOAL_MAX + " 之间");
        }
        if (dto.getFontSize() < FONT_SIZE_MIN || dto.getFontSize() > FONT_SIZE_MAX) {
            throw new BizException("字号须在 " + FONT_SIZE_MIN + "~" + FONT_SIZE_MAX + " 之间");
        }
        UserDO user = loadCurrentUser();
        user.setThemeColor(dto.getThemeColor());
        user.setThemeMode(dto.getThemeMode());
        user.setNoteBg(dto.getNoteBg());
        user.setWeatherCity(dto.getWeatherCity());
        user.setDailyGoal(dto.getDailyGoal());
        user.setFontSize(dto.getFontSize());
        if (dto.getAiFeatureMap() != null) {
            user.setAiFeatureMap(dto.getAiFeatureMap());
        }
        user.setUpdateTime(LocalDateTime.now());
        return toVO(userDAO.save(user));
    }

    /**
     * 校验旧密码后修改密码。
     *
     * <p>旧密码错误与账号不存在一样要防试探，但此处已是登录态，直接给 OLD_PASSWORD_WRONG
     * 便于用户自查。新旧相同则拒绝：改密的意义在于换掉旧口令，写一条一样的密文只会白白
     * 产生一次 BCrypt 计算与 update_time 变化。</p>
     *
     * @param dto 密码更新入参
     */
    @Override
    public void updatePassword(PasswordUpdateDTO dto) {
        UserDO user = loadCurrentUser();
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BizException(ResultCode.OLD_PASSWORD_WRONG);
        }
        if (passwordEncoder.matches(dto.getNewPassword(), user.getPassword())) {
            throw new BizException("新密码不能与当前密码相同");
        }
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        user.setUpdateTime(LocalDateTime.now());
        userDAO.save(user);
    }

    /**
     * 按当前登录身份加载用户实体。
     *
     * <p>findById 查不到说明令牌对应的账号已被删除（库被人手工清理等），
     * 用 NOT_FOUND 并附中文说明，而不是让后续字段访问空指针炸成 500。</p>
     *
     * @return 当前登录用户实体
     */
    private UserDO loadCurrentUser() {
        Long userId = UserContext.require();
        return userDAO.findById(userId)
                .orElseThrow(() -> new BizException(ResultCode.NOT_FOUND, "用户不存在或已被删除"));
    }

    /**
     * 实体转视图对象：只拷贝契约允许回显的字段，password、status 等内部字段永不入 VO。
     *
     * <p>显式逐字段赋值而非反射拷贝，敏感字段不会因为"实体加了列"被自动带出去。</p>
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
        vo.setAiFeatureMap(user.getAiFeatureMap());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }
}
