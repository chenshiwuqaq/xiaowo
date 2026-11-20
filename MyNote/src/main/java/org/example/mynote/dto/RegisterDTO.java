package org.example.mynote.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册入参。
 *
 * <p>只承载注册时必须由用户决定的字段：头像、背景、主题等一律由服务端写入默认值，
 * 不接受客户端多传字段，避免借此绕过业务约束（比如偷偷把自己注册成禁用状态）。
 * account 是注册后不可修改的登录凭证，所以在此按契约正则严格校验，宁可入口收紧。</p>
 *
 * @author MyNote
 */
@Data
public class RegisterDTO {

    /**
     * 登录账号：4-20 位字母/数字/下划线，与 API 契约的正则逐字一致
     */
    @NotBlank(message = "账号不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_]{4,20}$", message = "账号须为4-20位字母、数字或下划线")
    private String account;

    /**
     * 昵称：展示用名字，注册后可以随时改，长度与 mn_user.username 列对齐
     */
    @NotBlank(message = "昵称不能为空")
    @Size(max = 50, message = "昵称长度不能超过50个字符")
    private String username;

    /**
     * 明文密码：仅在传输层出现，落库前 BCrypt 散列，接口绝不回显
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度须为6-32位")
    private String password;

    /**
     * 邮箱：找回密码依赖"账号 + 注册邮箱"双因子核对，注册时必填
     */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度不能超过100个字符")
    private String email;
}
