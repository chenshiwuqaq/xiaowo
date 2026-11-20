package org.example.mynote.controller;

import jakarta.validation.Valid;
import org.example.mynote.common.Result;
import org.example.mynote.dto.PasswordUpdateDTO;
import org.example.mynote.dto.ProfileUpdateDTO;
import org.example.mynote.dto.SettingsDTO;
import org.example.mynote.service.UserService;
import org.example.mynote.vo.UserVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人资料与设置接口。
 *
 * <p>路径不在 /api/auth 白名单内，全部要求登录；控制器不接收任何用户身份参数，
 * 当前用户完全由拦截器写入的 UserContext 决定，"改谁的资料"不存在于请求里。</p>
 *
 * @author MyNote
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 查询当前登录用户的完整资料（含设置项），前端启动时用一次即可拉齐。
     *
     * @return 用户视图对象
     */
    @GetMapping("/profile")
    public Result<UserVO> getProfile() {
        return Result.ok(userService.getProfile());
    }

    /**
     * 更新昵称、邮箱、签名；account 不接收也不可改，由 DTO 与实体双重保证。
     *
     * @param dto 资料更新入参
     * @return 更新后的用户视图对象
     */
    @PutMapping("/profile")
    public Result<UserVO> updateProfile(@Valid @RequestBody ProfileUpdateDTO dto) {
        return Result.ok(userService.updateProfile(dto));
    }

    /**
     * 更新外观与写作习惯设置，数值越界返回带区间的中文提示。
     *
     * @param dto 设置更新入参
     * @return 更新后的用户视图对象
     */
    @PutMapping("/settings")
    public Result<UserVO> updateSettings(@Valid @RequestBody SettingsDTO dto) {
        return Result.ok(userService.updateSettings(dto));
    }

    /**
     * 修改当前用户密码，需校验旧密码，改完不重发令牌（当前会话继续有效）。
     *
     * @param dto 密码更新入参
     * @return 空数据的成功响应
     */
    @PutMapping("/password")
    public Result<Void> updatePassword(@Valid @RequestBody PasswordUpdateDTO dto) {
        userService.updatePassword(dto);
        return Result.ok();
    }
}
