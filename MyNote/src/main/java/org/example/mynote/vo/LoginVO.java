package org.example.mynote.vo;

import lombok.Data;

/**
 * 登录/注册成功返回：令牌 + 用户资料。
 *
 * <p>注册成功即签发令牌是契约约定（省一次登录往返），所以注册与登录复用同一个结构，
 * 前端两条链路可以用同一套"存 token、渲染用户信息"的代码。</p>
 *
 * @author MyNote
 */
@Data
public class LoginVO {

    /**
     * JWT 令牌，前端放入 Authorization: Bearer 头
     */
    private String token;

    /**
     * 当前登录用户资料（不含密码）
     */
    private UserVO user;
}
