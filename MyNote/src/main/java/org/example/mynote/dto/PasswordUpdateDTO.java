package org.example.mynote.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码入参。
 *
 * <p>必须带旧密码：改密发生在已登录会话中，二次确认口令可防"锁屏后被人顺手改密码"。
 * 校验旧密码是否正确、新旧是否相同都在 Service 里做，因为要读库拿 BCrypt 密文比对。</p>
 *
 * @author MyNote
 */
@Data
public class PasswordUpdateDTO {

    /**
     * 当前密码（明文），用 BCrypt matches 比对
     */
    @NotBlank(message = "当前密码不能为空")
    private String oldPassword;

    /**
     * 新密码，长度规则与注册一致
     */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 32, message = "新密码长度须为6-32位")
    private String newPassword;
}
