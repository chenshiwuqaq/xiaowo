package org.example.mynote.service.impl;

import org.example.mynote.ai.NoteContextBuilder;
import org.example.mynote.dao.KnowledgeCardDAO;
import org.example.mynote.dao.NoteDAO;
import org.example.mynote.entity.KnowledgeCardDO;
import org.example.mynote.entity.NoteDO;
import org.example.mynote.service.KnowledgeService;
import org.example.mynote.util.KeywordUtil;
import org.example.mynote.util.WordCountUtil;
import org.example.mynote.vo.KnowledgeResultVO;
import org.example.mynote.vo.KnowledgeVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 背景知识浮现实现。
 *
 * <p>思路是"本地命中"：用本地知识卡片按关键词命中，直接把命中的卡片呈现给作者，
 * 不额外调用模型生成提示，避免啰嗦的"深度思考"内容盖过真正有用的卡片常识。</p>
 *
 * <p>相关度 = 关键词命中次数 × 卡片权重（历史被采纳次数轻微加权），
 * 同分时优先时代/地域更明确的卡片，避免"泛泛的写作技法"盖掉"唐代坊市"这种真正有用的内容。</p>
 *
 * @author MyNote
 */
@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    /**
     * 最多浮现的卡片数，飘窗不是文献综述
     */
    private static final int MAX_CARDS = 5;

    /**
     * 低于该分值的卡片不展示，防止单个巧合字命中就弹卡片
     */
    private static final int MIN_SCORE = 2;

    /**
     * 低于该长度的正文不做"高频词"提示，短句抽出来的只会是句子碎片
     */
    private static final int SHORT_TEXT_CHARS = 120;

    private final KnowledgeCardDAO knowledgeCardDAO;

    private final NoteDAO noteDAO;

    private final NoteContextBuilder contextBuilder;

    public KnowledgeServiceImpl(KnowledgeCardDAO knowledgeCardDAO, NoteDAO noteDAO,
                                NoteContextBuilder contextBuilder) {
        this.knowledgeCardDAO = knowledgeCardDAO;
        this.noteDAO = noteDAO;
        this.contextBuilder = contextBuilder;
    }

    @Override
    public KnowledgeResultVO surface(Long userId, Long noteId, String text) {
        NoteDO note = noteId == null ? null : noteDAO.findFirstByIdAndUserId(noteId, userId);
        String haystack = buildHaystack(note, text);
        KnowledgeResultVO result = new KnowledgeResultVO();
        if (haystack.isEmpty()) {
            result.setMode("general");
            result.setModeName("通用");
            result.setCards(new ArrayList<>());
            result.setTriggerWords(new ArrayList<>());
            result.setReason("正文还太少，等写下一些具体内容我再浮现背景知识。");
            return result;
        }
        List<KnowledgeCardDO> cards = knowledgeCardDAO.findAllByOrderByIdAsc();
        List<KnowledgeVO> hits = matchCards(cards, haystack);
        Set<String> triggers = new LinkedHashSet<>();
        for (KnowledgeVO card : hits) {
            triggers.addAll(card.getHitWords());
        }
        String mode = note == null ? "general" : contextBuilder.modeOf(note.getNoteType());
        result.setMode(mode);
        result.setModeName("research".equals(mode) ? "科研" : "creation".equals(mode) ? "小说"
                : "life".equals(mode) ? "生活" : "通用");
        result.setCards(hits);
        result.setTriggerWords(new ArrayList<>(triggers));
        result.setReason(hits.isEmpty() ? buildMissReason(haystack)
                : "检测到「" + String.join("」「", limit(new ArrayList<>(triggers), 3))
                + "」，浮现这些背景常识供你核对。");
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adopt(Long cardId) {
        knowledgeCardDAO.increaseRefCount(cardId);
    }

    /**
     * 关键词命中打分。
     *
     * @param cards    全部卡片
     * @param haystack 待匹配文本
     * @return 排序后的命中结果
     */
    private List<KnowledgeVO> matchCards(List<KnowledgeCardDO> cards, String haystack) {
        List<KnowledgeVO> hits = new ArrayList<>();
        for (KnowledgeCardDO card : cards) {
            List<String> words = new ArrayList<>(6);
            int times = KeywordUtil.countHits(haystack, card.getKeywords(), words);
            if (times <= 0) {
                continue;
            }
            // 采纳次数多的卡片略加权，但上限压在 1.5 倍，避免"富者恒富"
            double boost = 1.0D + Math.min(card.getRefCount() == null ? 0 : card.getRefCount(), 50) / 100.0D;
            int score = (int) Math.round(times * boost);
            if (score < MIN_SCORE) {
                continue;
            }
            KnowledgeVO vo = new KnowledgeVO();
            vo.setId(card.getId());
            vo.setCategory(card.getCategory());
            vo.setEra(card.getEra());
            vo.setRegion(card.getRegion());
            vo.setTitle(card.getTitle());
            vo.setContent(card.getContent());
            vo.setSource(card.getSource());
            vo.setHitWords(words);
            vo.setScore(score);
            hits.add(vo);
        }
        hits.sort(Comparator.comparingInt(KnowledgeVO::getScore).reversed());
        return hits.size() > MAX_CARDS ? new ArrayList<>(hits.subList(0, MAX_CARDS)) : hits;
    }

    /**
     * 拼接待匹配文本：正文 + 选区 + 标题。
     *
     * @param note 笔记，可为空
     * @param text 额外文本
     * @return 文本
     */
    private String buildHaystack(NoteDO note, String text) {
        StringBuilder sb = new StringBuilder(512);
        if (note != null) {
            sb.append(WordCountUtil.stripMarkdown(note.getContent() == null ? "" : note.getContent()));
            sb.append(' ').append(note.getTitle() == null ? "" : note.getTitle());
        }
        if (text != null && !text.isEmpty()) {
            sb.append(' ').append(WordCountUtil.stripMarkdown(text));
        }
        return sb.toString();
    }

    /**
     * 没命中时给一句有用的解释，而不是空面板。
     *
     * @param haystack 文本
     * @return 文案
     */
    private String buildMissReason(String haystack) {
        if (haystack.length() < SHORT_TEXT_CHARS) {
            return "正文还太短，暂时没有可核对的背景知识。";
        }
        List<String> words = KeywordUtil.extract(haystack, 3);
        if (words.isEmpty()) {
            return "还没有可识别的地点、时代或主题词。";
        }
        return "暂未匹配到背景知识。你的文本里高频出现「" + String.join("」「", words)
                + "」，如果这是特定时代或地点的设定，可以在知识库补一条同名词的卡片。";
    }

    private static <T> List<T> limit(List<T> list, int size) {
        return list.size() <= size ? list : list.subList(0, size);
    }
}
