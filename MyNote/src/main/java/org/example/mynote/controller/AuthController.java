package org.example.mynote.controller;

import jakarta.validation.Valid;
import org.example.mynote.common.Result;
import org.example.mynote.dto.LoginDTO;
import org.example.mynote.dto.RegisterDTO;
import org.example.mynote.dto.ResetPasswordDTO;
import org.example.mynote.service.AuthService;
import org.example.mynote.vo.LoginVO;
import org.example.mynote.vo.ProviderVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 账号与鉴权接口。
 *
 * <p>整个 /api/auth/** 已在 WebConfig 拦截器白名单放行，是本系统唯一的匿名入口；
 * 控制器只做参数绑定与响应封装，业务判断全部下沉到 AuthService。</p>
 *
 * @author MyNote
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 注册新用户，成功即返回令牌，前端可直接进入已登录态。
     *
     * @param dto 注册信息，@Valid 触发 DTO 上的契约校验（账号正则、密码长度等）
     * @return 令牌与用户资料
     */
    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterDTO dto) {
        return Result.ok(authService.register(dto));
    }

    /**
     * 账号密码登录。
     *
     * @param dto 登录信息
     * @return 令牌与用户资料
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.ok(authService.login(dto));
    }

    /**
     * 自助重置密码：账号 + 注册邮箱双因子核对，成功后不签发令牌。
     *
     * @param dto 重置信息
     * @return 空数据的成功响应
     */
    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordDTO dto) {
        authService.resetPassword(dto);
        return Result.ok();
    }

    /**
     * 探测账号是否可注册，供注册表单失焦时实时提示。
     *
     * @param account 待探测账号
     * @return true 表示可用
     */
    @GetMapping("/check-account")
    public Result<Boolean> checkAccount(@RequestParam String account) {
        return Result.ok(authService.checkAccount(account));
    }

    /**
     * 获取预置 AI 服务商列表，无需登录即可供配置页渲染下拉。
     *
     * @return 服务商列表
     */
    @GetMapping("/providers")
    public Result<List<ProviderVO>> providers() {
        return Result.ok(authService.listProviders());
    }
}
