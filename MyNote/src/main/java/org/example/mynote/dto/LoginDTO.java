package org.example.mynote.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录入参。
 *
 * <p>account 不加格式正则而只校验非空：格式不符的输入应统一落到"账号或密码不正确"分支，
 * 而不是返回参数错误，让登录失败路径只有一种提示，不给任何人试探账号是否存在的机会。</p>
 *
 * @author MyNote
 */
@Data
public class LoginDTO {

    /**
     * 登录账号
     */
    @NotBlank(message = "账号不能为空")
    private String account;

    /**
     * 明文密码，服务端用 BCrypt matches 比对
     */
    @NotBlank(message = "密码不能为空")
    private String password;
}
