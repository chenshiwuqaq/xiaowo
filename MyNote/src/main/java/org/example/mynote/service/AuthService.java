package org.example.mynote.service;

import org.example.mynote.dto.LoginDTO;
import org.example.mynote.dto.RegisterDTO;
import org.example.mynote.dto.ResetPasswordDTO;
import org.example.mynote.vo.LoginVO;
import org.example.mynote.vo.ProviderVO;

import java.util.List;

/**
 * 账号鉴权服务：注册、登录、自助重置密码、可用性探测与预置服务商列表。
 *
 * <p>本服务是唯一允许匿名访问的业务入口，所有失败都以 BizException 表达，
 * 由全局异常处理器统一套上响应信封，实现层不再关心 HTTP 细节。</p>
 *
 * @author MyNote
 */
public interface AuthService {

    /**
     * 注册新用户并直接签发登录令牌。
     *
     * @param dto 注册信息（账号/昵称/密码/邮箱）
     * @return 令牌与用户资料，省去注册后再登录一次
     */
    LoginVO register(RegisterDTO dto);

    /**
     * 账号密码登录。
     *
     * @param dto 登录信息
     * @return 令牌与用户资料
     */
    LoginVO login(LoginDTO dto);

    /**
     * 通过"账号 + 注册邮箱"双因子核对自助重置密码，不签发令牌。
     *
     * @param dto 重置信息
     */
    void resetPassword(ResetPasswordDTO dto);

    /**
     * 探测账号是否可用于注册。
     *
     * @param account 待探测账号
     * @return true 表示未被占用、可注册
     */
    Boolean checkAccount(String account);

    /**
     * 获取预置 AI 服务商列表，供前端配置下拉。
     *
     * @return 服务商列表
     */
    List<ProviderVO> listProviders();
}
