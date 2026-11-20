package org.example.mynote.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 个人资料更新入参。
 *
 * <p>故意不定义 account 字段，也不定义 status/id 等字段：契约要求"账号不可修改"，
 * 从入参对象层面杜绝客户端提交这些值；更新时服务端只取本 DTO 声明的三个字段写回。</p>
 *
 * @author MyNote
 */
@Data
public class ProfileUpdateDTO {

    /**
     * 昵称
     */
    @NotBlank(message = "昵称不能为空")
    @Size(max = 50, message = "昵称长度不能超过50个字符")
    private String username;

    /**
     * 邮箱：仍是找回密码的核验因子，更新时必须保留合法格式
     */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度不能超过100个字符")
    private String email;

    /**
     * 一句话签名：允许为空（清空签名），只限制长度与数据库列一致
     */
    @Size(max = 255, message = "个性签名长度不能超过255个字符")
    private String intro;

    /**
     * 头像相对路径，由 /api/file/upload 返回后原样回传；为空表示不修改
     */
    @Size(max = 255, message = "头像路径过长")
    private String avatar;
}
