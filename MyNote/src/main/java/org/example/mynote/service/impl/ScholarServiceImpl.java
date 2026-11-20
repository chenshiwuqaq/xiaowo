package org.example.mynote.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.mynote.ai.AiRuntimeConfig;
import org.example.mynote.ai.NoteContextBuilder;
import org.example.mynote.ai.OpenAiClient;
import org.example.mynote.ai.PromptLibrary;
import org.example.mynote.common.BizException;
import org.example.mynote.config.AppProperties;
import org.example.mynote.dao.NoteDAO;
import org.example.mynote.dao.NoteReferenceDAO;
import org.example.mynote.dao.ReferenceDAO;
import org.example.mynote.entity.NoteDO;
import org.example.mynote.entity.NoteReferenceDO;
import org.example.mynote.entity.ReferenceDO;
import org.example.mynote.service.AiConfigService;
import org.example.mynote.service.ScholarService;
import org.example.mynote.util.KeywordUtil;
import org.example.mynote.util.SslUtil;
import org.example.mynote.util.WordCountUtil;
import org.example.mynote.vo.ReferenceResultVO;
import org.example.mynote.vo.ReferenceVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 文献推荐实现：本地文献库 + 联网最新结果 + 模型只做筛选。
 *
 * <p>三条线各自的分工是刻意设计的：</p>
 * <ul>
 *   <li>本地库（mn_reference）保证离线也能给出真实存在的条目；</li>
 *   <li>联网走 OpenAlex（无需 API Key），负责"最新"这一维度，结果会入库以便绑定归档；</li>
 *   <li>模型只从候选清单里挑选并写一句理由，<b>不允许生成新条目</b>——
 *       大模型编造参考文献是科研场景里最危险的失败模式，提示词里已写死这条禁令。</li>
 * </ul>
 *
 * @author chenjie
 */
@Service
public class ScholarServiceImpl implements ScholarService {

    private static final Logger log = LoggerFactory.getLogger(ScholarServiceImpl.class);

    private static final int MAX_KEYWORDS = 6;

    private static final int LOCAL_PER_KEYWORD = 6;

    private static final int MAX_ITEMS = 12;

    /**
     * 联网检索最多用几个词，太多会让上游接口排队变慢
     */
    private static final int ONLINE_KEYWORD_LIMIT = 3;

    private static final int RECENT_YEAR_SPAN = 3;

    /**
     * 模型二次排序的兜底超时：超时就退回规则打分，不该让一个慢模型拖垮整条推荐接口
     */
    private static final int MODEL_RANK_TIMEOUT_SECONDS = 15;

    private final ReferenceDAO referenceDAO;

    private final NoteReferenceDAO noteReferenceDAO;

    private final NoteDAO noteDAO;


    private final NoteContextBuilder contextBuilder;

    private final AiConfigService aiConfigService;

    private final OpenAiClient openAiClient;

    private final AppProperties appProperties;

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient;

    public ScholarServiceImpl(ReferenceDAO referenceDAO, NoteReferenceDAO noteReferenceDAO, NoteDAO noteDAO,
                              NoteContextBuilder contextBuilder,
                              AiConfigService aiConfigService, OpenAiClient openAiClient,
                              AppProperties appProperties, ObjectMapper objectMapper) {
        this.referenceDAO = referenceDAO;
        this.noteReferenceDAO = noteReferenceDAO;
        this.noteDAO = noteDAO;
        this.contextBuilder = contextBuilder;
        this.aiConfigService = aiConfigService;
        this.openAiClient = openAiClient;
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(appProperties.getScholar().getTimeoutSeconds()));
        if (appProperties.getHttp().isTrustAllCertificates()) {
            builder.sslContext(SslUtil.trustAllContext());
        }
        this.httpClient = builder.build();
    }

    @Override
    public ReferenceResultVO recommend(Long userId, Long noteId) {
        NoteDO note = noteId == null ? null : noteDAO.findFirstByIdAndUserId(noteId, userId);
        if (note == null) {
            throw new BizException("请先打开一篇笔记，助手才知道要找什么方向的文献");
        }
        String content = WordCountUtil.stripMarkdown(note.getContent());
        List<String> keywords = pickKeywords(content, note.getTitle());
        ReferenceResultVO result = new ReferenceResultVO();
        result.setMode(contextBuilder.modeOf(note.getNoteType()));
        result.setKeywords(keywords);

        Map<Long, ReferenceVO> merged = new java.util.LinkedHashMap<>();
        collectLocal(keywords, merged);
        StringBuilder onlineError = new StringBuilder();
        boolean online = collectOnline(keywords, merged, onlineError);

        List<ReferenceVO> items = new ArrayList<>(merged.values());
        items.sort(Comparator.comparingInt((ReferenceVO vo) -> vo.getScore() == null ? 0 : vo.getScore()).reversed());
        if (items.size() > MAX_ITEMS) {
            items = new ArrayList<>(items.subList(0, MAX_ITEMS));
        }
        markBound(noteId, items);
        result.setItems(items);
        result.setOnline(online);
        result.setOnlineError(online ? null : (onlineError.length() == 0 ? null : onlineError.toString()));
        result.setReason(buildReason(items, online, keywords));
        if (!items.isEmpty()) {
            rankByModel(userId, note, items);
            // 模型给出理由后按理由命中顺序再排一次，保证"最相关"在第一位
            items.sort(Comparator.comparingInt(vo -> vo.getScore() == null ? 0 : -vo.getScore()));
            result.setItems(items);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bind(Long userId, Long noteId, Long refId, String source) {
        requireOwnNote(userId, noteId);
        if (refId == null || refId <= 0) {
            throw new BizException("联网结果需要先入库才能绑定，请点“先入库再绑定”");
        }
        if (referenceDAO.findById(refId).isEmpty()) {
            throw new BizException("该文献已不在库中，请重新检索");
        }
        if (noteReferenceDAO.existsByNoteIdAndRefId(noteId, refId)) {
            return;
        }
        NoteReferenceDO relation = new NoteReferenceDO();
        relation.setNoteId(noteId);
        relation.setRefId(refId);
        relation.setSource(source == null ? "ai" : source);
        relation.setCreateTime(LocalDateTime.now());
        noteReferenceDAO.save(relation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbind(Long userId, Long noteId, Long refId) {
        requireOwnNote(userId, noteId);
        noteReferenceDAO.deleteByNoteAndRef(noteId, refId);
    }

    /**
     * 抽检索词：标题里的词权重更高，正文 n-gram 补足。
     *
     * @param content 正文纯文本
     * @param title   标题
     * @return 检索词
     */
    private List<String> pickKeywords(String content, String title) {
        List<String> fromTitle = KeywordUtil.extract(title, 3);
        List<String> fromBody = KeywordUtil.extract(content, MAX_KEYWORDS);
        List<String> keywords = new ArrayList<>(MAX_KEYWORDS);
        Set<String> seen = new LinkedHashSet<>();
        for (String word : fromTitle) {
            if (seen.add(word)) {
                keywords.add(word);
            }
        }
        for (String word : fromBody) {
            if (keywords.size() >= MAX_KEYWORDS) {
                break;
            }
            if (seen.add(word)) {
                keywords.add(word);
            }
        }
        return keywords;
    }

    /**
     * 本地库检索。
     *
     * @param keywords 检索词
     * @param merged   结果收集器
     */
    private void collectLocal(List<String> keywords, Map<Long, ReferenceVO> merged) {
        for (String keyword : keywords) {
            List<ReferenceDO> found = referenceDAO.matchByKeyword("%" + keyword + "%",
                    PageRequest.of(0, LOCAL_PER_KEYWORD));
            for (ReferenceDO ref : found) {
                ReferenceVO vo = merged.computeIfAbsent(ref.getId(), id -> toVO(ref, "local"));
                if (!vo.getMatched().contains(keyword)) {
                    vo.getMatched().add(keyword);
                }
                vo.setScore(scoreOf(vo, false));
            }
        }
    }

    /**
     * 联网检索 OpenAlex。失败时返回 false，让上层在文案里说明"当前为离线结果"。
     *
     * <p>多个检索词用 sendAsync 并发拉取而不是串行排队：每个词最多等一个 timeout，串行会把
     * "上游慢"放大成 N 倍，直接撞上前端超时。解析与入库仍在主线程顺序进行，
     * 避免对 merged / indexedTitles 的并发写。</p>
     *
     * @param keywords 检索词
     * @param merged   结果收集器
     * @return 是否拿到联网结果
     */
    private boolean collectOnline(List<String> keywords, Map<Long, ReferenceVO> merged, StringBuilder diag) {
        AppProperties.Scholar scholar = appProperties.getScholar();
        if (!scholar.isEnabled() || keywords.isEmpty()) {
            return false;
        }
        // 已入库标题只做一次全量取，避免每条联网结果都扫一遍表
        Map<String, Long> indexedTitles = loadTitleIndex();
        int recentYear = LocalDateTime.now().getYear() - RECENT_YEAR_SPAN;
        List<String> onlineKeywords = keywords.subList(0, Math.min(ONLINE_KEYWORD_LIMIT, keywords.size()));

        List<CompletableFuture<HttpResponse<String>>> futures = new ArrayList<>();
        for (String keyword : onlineKeywords) {
            futures.add(httpClient.sendAsync(buildOnlineRequest(scholar, keyword, recentYear),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)));
        }

        boolean anyOnline = false;
        boolean anyHttpOk = false;
        for (int i = 0; i < onlineKeywords.size(); i++) {
            String keyword = onlineKeywords.get(i);
            try {
                HttpResponse<String> response = futures.get(i).get(scholar.getTimeoutSeconds() + 2L, TimeUnit.SECONDS);
                if (response.statusCode() != 200) {
                    log.info("OpenAlex 返回状态 {}", response.statusCode());
                    appendDiag(diag, "接口返回 HTTP " + response.statusCode());
                    continue;
                }
                anyHttpOk = true;
                anyOnline |= ingest(response.body(), keyword, merged, indexedTitles);
            } catch (Exception e) {
                log.info("联网文献检索失败（本机文献库仍可用）: {}", e.getMessage());
                appendDiag(diag, "请求失败：" + WordCountUtil.truncate(
                        e.getMessage() == null ? "未知错误" : e.getMessage(), 120));
            }
        }
        if (!anyOnline && anyHttpOk && diag.length() == 0) {
            appendDiag(diag, "已请求到数据，但没有命中与检索词相关的文献");
        }
        return anyOnline;
    }

    /** 追加诊断信息，多条之间用中文分号分隔 */
    private static void appendDiag(StringBuilder diag, String msg) {
        if (diag.length() > 0) {
            diag.append('；');
        }
        diag.append(msg);
    }

    /**
     * 构造 OpenAlex 检索请求。
     *
     * @param scholar    联网配置
     * @param keyword    检索词
     * @param recentYear 起始年份
     * @return 请求
     */
    private HttpRequest buildOnlineRequest(AppProperties.Scholar scholar, String keyword, int recentYear) {
        // 不用 sort=cited_by_count：search 本身就是相关度排序，按被引排序反而让高被引无关论文挤进来
        String url = scholar.getBaseUrl() + "/works?search="
                + URLEncoder.encode(keyword, StandardCharsets.UTF_8)
                + "&filter=from_publication_date:" + recentYear + "-01-01"
                + "&per-page=" + scholar.getMaxResults()
                + "&mailto=" + URLEncoder.encode(
                scholar.getMailto() == null ? "" : scholar.getMailto(), StandardCharsets.UTF_8);
        return HttpRequest.newBuilder().uri(URI.create(url))
                .timeout(Duration.ofSeconds(scholar.getTimeoutSeconds())).GET().build();
    }

    /**
     * 建立"标题 -> 文献 id"的索引，键统一去空格并转小写，用于入库去重。
     *
     * @return 索引
     */
    private Map<String, Long> loadTitleIndex() {
        Map<String, Long> index = new HashMap<>(64);
        for (ReferenceDO exists : referenceDAO.findAll()) {
            if (exists.getTitle() != null) {
                index.put(normalizeTitle(exists.getTitle()), exists.getId());
            }
        }
        return index;
    }

    /**
     * 判断上游返回的条目是否真的与检索词相关（标题、概念、摘要任一命中）。
     *
     * @param item    OpenAlex 条目
     * @param keyword 检索词
     * @return true 表示相关
     */
    private boolean isRelevant(JsonNode item, String keyword) {
        // 中文检索词无法对英文语料做字面包含匹配，交给 OpenAlex 的 search 相关度排序即可
        if (keyword.chars().anyMatch(ch -> ch > 127)) {
            return true;
        }
        StringBuilder blob = new StringBuilder(256);
        blob.append(item.path("title").asText("")).append(' ');
        for (JsonNode concept : item.path("concepts")) {
            blob.append(concept.path("display_name").asText("")).append(' ');
        }
        String abstractText = abstractFromInverse(item);
        if (abstractText != null) {
            blob.append(abstractText);
        }
        return KeywordUtil.appearsIn(keyword, blob.toString(),
                item.path("doi").asText(""), item.path("id").asText(""));
    }

    private static String normalizeTitle(String title) {
        return title.trim().toLowerCase();
    }

    /**
     * 解析 OpenAlex 响应并入库（同标题已存在则复用，避免文献库重复膨胀）。
     *
     * @param body          响应体
     * @param keyword       触发检索的词
     * @param merged        结果收集器
     * @param indexedTitles 已入库标题索引，会被增量更新
     * @return 是否解析到条目
     */
    private boolean ingest(String body, String keyword, Map<Long, ReferenceVO> merged,
                           Map<String, Long> indexedTitles) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        boolean any = false;
        for (JsonNode item : root.path("results")) {
            String title = item.path("title").asText(null);
            if (title == null || title.isBlank()) {
                continue;
            }
            // 相关性闸门：上游按被引排序，不设这道关卡时高被引的无关论文会挤掉真正相关的条目
            if (!isRelevant(item, keyword)) {
                continue;
            }
            String normalized = normalizeTitle(title);
            Long existingId = indexedTitles.get(normalized);
            ReferenceDO stored = existingId == null ? null : referenceDAO.findById(existingId).orElse(null);
            if (stored == null) {
                stored = new ReferenceDO();
                stored.setTitle(title);
                List<String> names = new ArrayList<>();
                for (JsonNode author : item.path("authorships")) {
                    names.add(author.path("author").path("display_name").asText(""));
                }
                stored.setAuthors(WordCountUtil.truncate(String.join(", ", names), 480));
                stored.setJournal(item.path("primary_location").path("source").path("display_name").asText(null));
                stored.setPubYear(item.path("publication_year").asInt(0) == 0 ? null
                        : item.path("publication_year").asInt());
                stored.setDoi(item.path("doi").asText(null));
                List<String> concepts = new ArrayList<>();
                for (JsonNode concept : item.path("concepts")) {
                    concepts.add(concept.path("display_name").asText(""));
                }
                stored.setKeywords(WordCountUtil.truncate(String.join(",", concepts), 480));
                stored.setAbstractText(abstractFromInverse(item));
                stored.setCitedCount(item.path("cited_by_count").asInt(0));
                stored.setUrl(item.path("url").asText(null));
                stored.setCreateTime(LocalDateTime.now());
                stored = referenceDAO.save(stored);
                indexedTitles.put(normalized, stored.getId());
            }
            final ReferenceDO resolved = stored;
            ReferenceVO vo = merged.computeIfAbsent(resolved.getId(), id -> toVO(resolved, "online"));
            if (!vo.getMatched().contains(keyword)) {
                vo.getMatched().add(keyword);
            }
            vo.setScore(scoreOf(vo, true));
            any = true;
        }
        return any;
    }

    /**
     * OpenAlex 用倒排索引存摘要（abstract_inverted_index），需要按词位重建。
     *
     * @param item 原始节点
     * @return 摘要文本，没有则返回 null
     */
    private String abstractFromInverse(JsonNode item) {
        JsonNode inverted = item.path("abstract_inverted_index");
        if (inverted.isMissingNode() || inverted.isEmpty()) {
            return null;
        }
        Map<Integer, String> slots = new java.util.TreeMap<>();
        java.util.Iterator<Map.Entry<String, JsonNode>> fields = inverted.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            for (JsonNode position : entry.getValue()) {
                slots.putIfAbsent(position.asInt(), entry.getKey());
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String word : slots.values()) {
            sb.append(word).append(' ');
        }
        return WordCountUtil.truncate(sb.toString().trim(), 1000);
    }

    /**
    /**
     * 相关度：命中词数为主，联网新条目与高被引各加一点。
     *
     * @param vo     结果
     * @param recent 是否来自联网
     * @return 分值
     */
    private int scoreOf(ReferenceVO vo, boolean recent) {
        int score = vo.getMatched().size() * 10;
        if (vo.getCitedCount() != null) {
            score += Math.min(vo.getCitedCount() / 1000, 8);
        }
        if (recent) {
            score += 4;
        }
        if (vo.getPubYear() != null && vo.getPubYear() >= LocalDateTime.now().getYear() - 2) {
            score += 3;
        }
        return score;
    }

    /**
     * 标记哪些文献已经绑在当前笔记上，前端据此显示"已收录"。
     *
     * @param noteId 笔记 id
     * @param items  结果
     */
    private void markBound(Long noteId, List<ReferenceVO> items) {
        Set<Long> bound = new LinkedHashSet<>();
        for (NoteReferenceDO relation : noteReferenceDAO.findByNoteId(noteId)) {
            bound.add(relation.getRefId());
        }
        for (ReferenceVO item : items) {
            item.setBound(bound.contains(item.getId()));
        }
    }

    /**
     * 让模型在候选里排序并写理由。失败不影响返回，退化为按规则打分排序。
     *
     * @param userId 用户 id
     * @param note   笔记
     * @param items  候选
     */
    private void rankByModel(Long userId, NoteDO note, List<ReferenceVO> items) {
        AiRuntimeConfig runtime = aiConfigService.currentRuntime(userId);
        if (runtime == null) {
            return;
        }
        StringBuilder candidates = new StringBuilder(1024);
        for (int i = 0; i < items.size(); i++) {
            ReferenceVO vo = items.get(i);
            candidates.append(i + 1).append(". ").append(vo.getTitle()).append(" | ")
                    .append(vo.getAuthors() == null ? "作者未知" : vo.getAuthors()).append(" | ")
                    .append(vo.getJournal() == null ? "" : vo.getJournal()).append(' ').append(vo.getPubYear())
                    .append(" | ").append(WordCountUtil.truncate(
                    vo.getAbstractText() == null ? "" : vo.getAbstractText(), 120)).append('\n');
        }
        try {
            List<OpenAiClient.Message> messages = new ArrayList<>(2);
            messages.add(new OpenAiClient.Message("system", PromptLibrary.SCENE_REFERENCE_PICK));
            messages.add(new OpenAiClient.Message("user",
                    PromptLibrary.contextBlock("【标题】" + note.getTitle() + "\n【正文】"
                            + WordCountUtil.truncate(WordCountUtil.stripMarkdown(note.getContent()), 2000))
                            + "\n\n【候选文献清单】\n" + candidates));
            String answer = openAiClient.chatOnce(runtime, messages,
                    Duration.ofSeconds(MODEL_RANK_TIMEOUT_SECONDS));
            applyReasons(answer, items);
        } catch (Exception e) {
            log.info("文献推荐未使用模型排序: {}", e.getMessage());
        }
    }

    /**
     * 解析模型返回的「编号 | 理由」行，把理由写进 abstractText 之后的展示位并加权重。
     *
     * @param answer 模型输出
     * @param items  候选
     */
    private void applyReasons(String answer, List<ReferenceVO> items) {
        for (String line : answer.split("\n")) {
            String clean = line.trim();
            int sep = clean.indexOf('|');
            if (sep <= 0) {
                continue;
            }
            // 编号可能带「1.」「1、」或「-」等列表符号，只取数字部分，避免把编号一起剥掉导致解析失败
            String numPart = clean.substring(0, sep).replaceAll("[^0-9]", "");
            if (numPart.isEmpty()) {
                continue;
            }
            int index;
            try {
                index = Integer.parseInt(numPart);
            } catch (NumberFormatException e) {
                continue;
            }
            if (index < 1 || index > items.size()) {
                continue;
            }
            ReferenceVO target = items.get(index - 1);
            String reason = clean.substring(sep + 1).trim();
            target.setReason(reason);
            target.setScore((target.getScore() == null ? 0 : target.getScore()) + 20 - index);
        }
    }

    /**
     * 说明文案，把"数据来源"讲清楚，用户才知道能不能信。
     *
     * @param items    结果
     * @param online   是否含联网结果
     * @param keywords 检索词
     * @return 文案
     */
    private String buildReason(List<ReferenceVO> items, boolean online, List<String> keywords) {
        if (items.isEmpty()) {
            return "用「" + String.join("」「", limit(keywords, 3))
                    + "」没找到相关文献。可以换个更具体的说法，或把内容写得再明确一点。";
        }
        return (online ? "已合并本机文献库与联网检索的最新结果" : "当前为本机文献库结果（联网检索未成功）")
                + "，共 " + items.size() + " 条，按相关度排序。";
    }

    private ReferenceVO toVO(ReferenceDO ref, String source) {
        ReferenceVO vo = new ReferenceVO();
        vo.setId(ref.getId());
        vo.setTitle(ref.getTitle());
        vo.setAuthors(ref.getAuthors());
        vo.setJournal(ref.getJournal());
        vo.setPubYear(ref.getPubYear());
        vo.setDoi(ref.getDoi());
        vo.setUrl(ref.getUrl());
        vo.setField(ref.getField());
        vo.setAbstractText(ref.getAbstractText());
        vo.setCitedCount(ref.getCitedCount());
        vo.setMatched(new ArrayList<>());
        vo.setScore(0);
        vo.setBound(false);
        vo.setSource(source);
        return vo;
    }


    private void requireOwnNote(Long userId, Long noteId) {
        if (noteId == null || noteDAO.findFirstByIdAndUserId(noteId, userId) == null) {
            throw new BizException(org.example.mynote.common.ResultCode.NOT_FOUND);
        }
    }

    private static List<String> limit(List<String> list, int size) {
        return list.size() <= size ? list : list.subList(0, size);
    }
}
