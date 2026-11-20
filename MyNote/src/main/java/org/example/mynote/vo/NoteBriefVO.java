package org.example.mynote.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 笔记卡片用的简要信息，对应 NoteDAO.NoteBrief 投影。
 *
 * <p>与 /api/note 列表的 NoteVO 相比，这里刻意不带 tags：主页的"最近在写"和"置顶"卡片
 * 只显示标题、摘要与更新时间，取标签需要再关联一次 mn_note_tag，为几个小圆点多打一趟查询不划算。
 * 前端如果需要标签，走 /api/note/list。</p>
 *
 * <p>createTime / updateTime 输出为 yyyy-MM-dd HH:mm:ss 字符串，理由同 {@link DailyWordVO}：
 * Jackson 的全局 date-format 管不到 JSR-310 类型，显式格式化才能保证契约里的格式一致。</p>
 *
 * @author MyNote
 */
@Data
public class NoteBriefVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /**
     * 标题，笔记标题为空时已由保存逻辑用正文首行推断
     */
    private String title;

    /**
     * 摘要，最长 500 字，直接用于卡片文案
     */
    private String summary;

    private Long folderId;

    /**
     * 类型编码，供前端配色
     */
    private Integer noteType;

    /**
     * 类型中文名，与 noteType 同源，避免前端各写一份码表
     */
    private String noteTypeName;

    /**
     * 当前字数（口径一：现存作品的字数）
     */
    private Integer wordCount;

    /**
     * 是否置顶
     */
    private Boolean top;

    /**
     * 创建时间，格式 yyyy-MM-dd HH:mm:ss
     */
    private String createTime;

    /**
     * 最后更新时间，格式 yyyy-MM-dd HH:mm:ss
     */
    private String updateTime;
}
