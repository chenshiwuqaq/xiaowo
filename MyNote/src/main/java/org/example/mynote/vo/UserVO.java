package org.example.mynote.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户视图对象，对前端回显的用户全量资料。
 *
 * <p>刻意不包含 password 字段（BCrypt 密文哪怕不可逆也不该出现在响应里），
 * 也不包含 status、updateTime 等内部字段，接口层面最小化信息暴露。
 * createTime 按契约以 "yyyy-MM-dd HH:mm:ss" 展示：application.yml 的
 * spring.jackson.date-format 只作用于 java.util.Date，对 LocalDateTime 生效
 * 必须在本字段显式加 @JsonFormat。</p>
 *
 * @author MyNote
 */
@Data
public class UserVO {

    /**
     * 用户 id
     */
    private Long id;

    /**
     * 登录账号，仅回显不可修改
     */
    private String account;

    /**
     * 昵称
     */
    private String username;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 头像相对路径
     */
    private String avatar;

    /**
     * 一句话签名
     */
    private String intro;

    /**
     * 主题强调色
     */
    private String themeColor;

    /**
     * 主题模式：light / dark
     */
    private String themeMode;

    /**
     * 笔记书页背景
     */
    private String noteBg;

    /**
     * 天气城市
     */
    private String weatherCity;

    /**
     * 每日字数目标
     */
    private Integer dailyGoal;

    /**
     * 正文字号
     */
    private Integer fontSize;

    /**
     * 笔记类型 × AI 功能开关的 JSON 配置
     */
    private String aiFeatureMap;

    /**
     * 注册时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
