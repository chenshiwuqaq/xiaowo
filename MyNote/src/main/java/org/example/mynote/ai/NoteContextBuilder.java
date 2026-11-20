package org.example.mynote.ai;

import org.example.mynote.common.Constants;
import org.example.mynote.config.AppProperties;
import org.example.mynote.dao.NoteDAO;
import org.example.mynote.entity.NoteDO;
import org.example.mynote.util.WordCountUtil;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI 上下文组装器。
 *
 * <p>助手要好用，必须知道"作者此刻在写什么、前后写过什么"。这里把当前笔记正文、
 * 同目录邻域摘要、编辑器选区拼成一段文本送进提示词；拼装全部在本机完成，
 * 只有最后调用模型那一步会把这段上下文发给用户自己选择的模型服务商。</p>
 *
 * <p>长度受 mynote.ai.max-context-chars 限制：长文取"头 + 尾 + 省略标记"，
 * 保留开头设定与最近的书写现场，比均匀截断更贴近作者当下的问题位置。</p>
 *
 * @author MyNote
 */
@Component
public class NoteContextBuilder {

    private static final int HEAD_RATIO = 60;

    private static final int SUMMARY_CHARS = 90;

    private final NoteDAO noteDAO;

    private final AppProperties appProperties;

    public NoteContextBuilder(NoteDAO noteDAO, AppProperties appProperties) {
        this.noteDAO = noteDAO;
        this.appProperties = appProperties;
    }

    /**
     * 组装结果。
     */
    public static class Context {

        private final NoteDO note;

        private final String noteBlock;

        private final String siblingBlock;

        private final String mode;

        private final String modeName;

        Context(NoteDO note, String noteBlock, String siblingBlock, String mode, String modeName) {
            this.note = note;
            this.noteBlock = noteBlock;
            this.siblingBlock = siblingBlock;
            this.mode = mode;
            this.modeName = modeName;
        }

        public NoteDO getNote() {
            return note;
        }

        public String getNoteBlock() {
            return noteBlock;
        }

        public String getSiblingBlock() {
            return siblingBlock;
        }

        public String getMode() {
            return mode;
        }

        public String getModeName() {
            return modeName;
        }

        /**
         * 上下文是否为空（笔记不存在或正文为空时前端应提示而不是发问）。
         *
         * @return true 表示无内容
         */
        public boolean isEmpty() {
            return note == null;
        }
    }

    /**
     * 按笔记与用户组装上下文。
     *
     * @param userId 用户 id，越权时返回空上下文而不是异常，助手不打断书写流程
     * @param noteId 笔记 id，可为空
     * @return 上下文
     */
    public Context build(Long userId, Long noteId) {
        if (noteId == null) {
            return new Context(null, "", "", "general", "自由对话");
        }
        NoteDO note = noteDAO.findFirstByIdAndUserId(noteId, userId);
        if (note == null) {
            return new Context(null, "", "", "general", "自由对话");
        }
        int limit = appProperties.getAi().getMaxContextChars();
        StringBuilder sb = new StringBuilder(512);
        sb.append("【标题】").append(note.getTitle()).append('\n');
        sb.append("【类型】").append(modeNameOf(note.getNoteType())).append('\n');
        if (note.getSummary() != null && !note.getSummary().isEmpty()) {
            sb.append("【摘要】").append(note.getSummary()).append('\n');
        }
        sb.append("【正文】").append(trimMiddle(WordCountUtil.stripMarkdown(note.getContent()), limit));
        return new Context(note, sb.toString(), buildSiblings(userId, note), modeOf(note.getNoteType()),
                modeNameOf(note.getNoteType()));
    }

    /**
     * 同目录邻域摘要，用于一致性检查与延续写作风格。
     *
     * @param userId 用户 id
     * @param note   当前笔记
     * @return 邻域文本，无邻域时返回空串
     */
    private String buildSiblings(Long userId, NoteDO note) {
        List<NoteDO> siblings = noteDAO.findSiblings(userId, note.getFolderId(), note.getId(),
                PageRequest.of(0, appProperties.getAi().getMaxSiblingNotes()));
        if (siblings.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(256);
        for (NoteDO sibling : siblings) {
            String brief = sibling.getSummary() != null && !sibling.getSummary().isEmpty()
                    ? sibling.getSummary()
                    : WordCountUtil.summarize(sibling.getContent(), SUMMARY_CHARS);
            sb.append("- 《").append(sibling.getTitle()).append("》：").append(brief).append('\n');
        }
        return sb.toString();
    }

    /**
     * 由笔记类型推导助手模式：科研走文献推荐分支，小说/生活走背景知识分支。
     *
     * @param noteType 类型编码
     * @return research / creation / life / general
     */
    public String modeOf(Integer noteType) {
        if (noteType == null) {
            return "general";
        }
        if (noteType == Constants.NOTE_TYPE_RESEARCH) {
            return "research";
        }
        if (noteType == Constants.NOTE_TYPE_FICTION) {
            return "creation";
        }
        if (noteType == Constants.NOTE_TYPE_LIFE) {
            return "life";
        }
        return "general";
    }

    /**
     * 模式中文名，用于前端直接展示。
     *
     * @param noteType 类型编码
     * @return 中文名
     */
    public String modeNameOf(Integer noteType) {
        if (noteType == null) {
            return "随笔";
        }
        if (noteType == Constants.NOTE_TYPE_RESEARCH) {
            return "科研";
        }
        if (noteType == Constants.NOTE_TYPE_LIFE) {
            return "生活";
        }
        if (noteType == Constants.NOTE_TYPE_FICTION) {
            return "小说";
        }
        return "随笔";
    }

    /**
     * 头尾保留式截断。
     *
     * @param text  原文
     * @param limit 上限字符数
     * @return 截断结果
     */
    private String trimMiddle(String text, int limit) {
        if (text == null || text.length() <= limit) {
            return text == null ? "" : text;
        }
        int head = limit * HEAD_RATIO / 100;
        int tail = limit - head;
        return text.substring(0, head) + "\n……（中间省略 " + (text.length() - limit) + " 字）……\n"
                + text.substring(text.length() - tail);
    }
}
