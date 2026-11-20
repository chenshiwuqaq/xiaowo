package org.example.mynote.service;

import org.example.mynote.vo.KnowledgeResultVO;

/**
 * 背景知识浮现服务（生活 / 小说创作分支）。
 *
 * @author MyNote
 */
public interface KnowledgeService {

    /**
     * 扫描笔记正文，浮现与写作内容相关的时代与生活常识卡片。
     *
     * @param userId 用户 id
     * @param noteId 笔记 id
     * @param text   额外文本（例如编辑器选区），与正文一起参与命中
     * @return 命中结果；无命中时 cards 为空并给出说明文案
     */
    KnowledgeResultVO surface(Long userId, Long noteId, String text);

    /**
     * 采纳一次卡片，用于统计哪些常识真的被写进正文。
     *
     * @param cardId 卡片 id
     */
    void adopt(Long cardId);
}
