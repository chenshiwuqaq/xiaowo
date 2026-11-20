package org.example.mynote.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 主页开场白。
 *
 * <p>单独一个接口而不并进 overview，是因为它会随时间变化：前端可以每次回到主页时只刷新这一小块，
 * 不必重跑一遍统计与天气。</p>
 *
 * @author MyNote
 */
@Data
public class GreetingVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 按时段给出的问候语，带昵称
     */
    private String greeting;

    /**
     * 副标题：当天日期与星期，给用户"此刻"的坐标感
     */
    private String subtitle;

    /**
     * 两条随机小贴士；今日字数达标时会再追加一句鼓励
     */
    private List<String> tips;
}
