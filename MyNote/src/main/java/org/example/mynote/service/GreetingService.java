package org.example.mynote.service;

import org.example.mynote.vo.GreetingVO;

/**
 * 主页开场白服务：问候语、当日日期与小贴士。
 *
 * <p>问候是纯展示逻辑，不进 overview，前端每次回到主页单独刷新即可，
 * 既省掉一遍全表统计，也能让早中午晚的措辞跟着时间走。</p>
 *
 * @author MyNote
 */
public interface GreetingService {

    /**
     * 生成当前登录用户此刻的问候。
     *
     * @return 问候语、日期副标题与小贴士列表
     */
    GreetingVO greet();
}
