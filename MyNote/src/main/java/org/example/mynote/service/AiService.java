package org.example.mynote.service;

import org.example.mynote.dto.AiActionDTO;
import org.example.mynote.dto.AiApplyDTO;
import org.example.mynote.dto.AiChatDTO;
import org.example.mynote.dto.AiRewriteDTO;
import org.example.mynote.dto.AiSuggestDTO;
import org.example.mynote.vo.AiActionVO;
import org.example.mynote.vo.AiMessageVO;
import org.example.mynote.vo.AiSessionVO;
import org.example.mynote.vo.AiStatusVO;
import org.example.mynote.vo.NoteDetailVO;
import org.example.mynote.vo.RewriteVO;
import org.example.mynote.vo.SuggestVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * AI 助手服务。
 *
 * @author MyNote
 */
public interface AiService {

    /**
     * 助手可用状态与能力清单。
     *
     * @param userId 用户 id
     * @return 状态
     */
    AiStatusVO status(Long userId);

    /**
     * 流式对话。
     *
     * @param userId 用户 id
     * @param dto    对话入参
     * @return SSE 发射器
     */
    SseEmitter chatStream(Long userId, AiChatDTO dto);

    /**
     * 快捷写作动作（不改动笔记，只返回文本）。
     *
     * @param userId 用户 id
     * @param dto    动作入参
     * @return 输出文本
     */
    AiActionVO action(Long userId, AiActionDTO dto);

    /**
     * 改写预览：返回原文与新文本，不落库。
     *
     * @param userId 用户 id
     * @param dto    改写入参
     * @return 预览结果
     */
    RewriteVO rewrite(Long userId, AiRewriteDTO dto);

    /**
     * 用户确认预览后，把新文本覆盖进笔记。
     *
     * @param userId 用户 id
     * @param dto    替换入参
     * @return 更新后的笔记详情
     */
    NoteDetailVO applyRewrite(Long userId, AiApplyDTO dto);

    /**
     * 飘窗建议：停顿或选中时浮出 2-3 条此刻可能想问的问题。
     *
     * @param userId 用户 id
     * @param dto    入参
     * @return 建议列表
     */
    SuggestVO suggest(Long userId, AiSuggestDTO dto);

    /**
     * 会话列表。
     *
     * @param userId 用户 id
     * @param noteId 笔记 id，可为空
     * @return 会话
     */
    List<AiSessionVO> sessions(Long userId, Long noteId);

    /**
     * 会话消息。
     *
     * @param userId    用户 id
     * @param sessionId 会话 id
     * @return 消息
     */
    List<AiMessageVO> messages(Long userId, Long sessionId);

    /**
     * 删除会话及其消息。
     *
     * @param userId    用户 id
     * @param sessionId 会话 id
     */
    void deleteSession(Long userId, Long sessionId);
}
