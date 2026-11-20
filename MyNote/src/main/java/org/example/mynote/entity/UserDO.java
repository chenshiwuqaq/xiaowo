package org.example.mynote.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户数据对象，对应表 mn_user。
 *
 * <p>account 为登录账号，注册后不可修改；username 为可改昵称。password 存 BCrypt 密文，
 * 任何时候都不回显给前端。</p>
 *
 * @author MyNote
 */
@Data
@Entity
@Table(name = "mn_user")
public class UserDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 登录账号，注册后不可修改
     */
    @Column(name = "account", nullable = false, length = 50)
    private String account;

    /**
     * 昵称
     */
    @Column(name = "username", nullable = false, length = 50)
    private String username;

    /**
     * BCrypt 密文
     */
    @Column(name = "password", nullable = false, length = 100)
    private String password;

    @Column(name = "email", length = 100)
    private String email;

    /**
     * 头像相对路径
     */
    @Column(name = "avatar", length = 255)
    private String avatar;

    /**
     * 一句话签名
     */
    @Column(name = "intro", length = 255)
    private String intro;

    @Column(name = "theme_color", length = 20)
    private String themeColor;

    /**
     * light / dark
     */
    @Column(name = "theme_mode", length = 20)
    private String themeMode;

    /**
     * 笔记书页背景标识或自定义图片路径
     */
    @Column(name = "note_bg", length = 255)
    private String noteBg;

    @Column(name = "weather_city", length = 50)
    private String weatherCity;

    /**
     * 每日字数目标
     */
    @Column(name = "daily_goal")
    private Integer dailyGoal;

    @Column(name = "font_size")
    private Integer fontSize;

    /**
     * 笔记类型 × AI 功能开关的 JSON 配置，形如 {"1":{"knowledge":false,"references":true}}
     */
    @Column(name = "ai_feature_map", columnDefinition = "TEXT")
    private String aiFeatureMap;

    /**
     * 1 正常，0 禁用
     */
    @Column(name = "status")
    private Integer status;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
