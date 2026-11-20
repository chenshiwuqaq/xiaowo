package org.example.mynote.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 自助重置密码入参。
 *
 * <p>本地自托管场景不接邮件服务（邮件通道反而是新的故障点），因此用"账号 + 注册邮箱"
 * 联合查询命中来替代邮件验证码；字段名与契约保持 newPassword，避免与登录密码混淆。</p>
 *
 * @author MyNote
 */
@Data
public class ResetPasswordDTO {

    /**
     * 登录账号，与 email 联合定位唯一用户
     */
    @NotBlank(message = "账号不能为空")
    private String account;

    /**
     * 注册邮箱，必须与账号绑定的一致才允许改密
     */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    /**
     * 新密码，长度规则与注册一致，防止重置成一个更弱的口令
     */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 32, message = "新密码长度须为6-32位")
    private String newPassword;
}
