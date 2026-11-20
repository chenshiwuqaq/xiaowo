package org.example.mynote.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.mynote.ai.AiRuntimeConfig;
import org.example.mynote.ai.NoteContextBuilder;
import org.example.mynote.ai.OpenAiClient;
import org.example.mynote.ai.PromptLibrary;
import org.example.mynote.common.BizException;
import org.example.mynote.common.Constants;
import org.example.mynote.common.ResultCode;
import org.example.mynote.dao.AiMessageDAO;
import org.example.mynote.dao.AiSessionDAO;
import org.example.mynote.dao.NoteDAO;
import org.example.mynote.dto.AiActionDTO;
import org.example.mynote.dto.AiApplyDTO;
import org.example.mynote.dto.AiChatDTO;
import org.example.mynote.dto.AiRewriteDTO;
import org.example.mynote.dto.AiSuggestDTO;
import org.example.mynote.dto.NoteUpdateDTO;
import org.example.mynote.entity.AiMessageDO;
import org.example.mynote.entity.AiSessionDO;
import org.example.mynote.entity.NoteDO;
import org.example.mynote.service.AiConfigService;
import org.example.mynote.service.AiService;
import org.example.mynote.service.NoteService;
import org.example.mynote.util.KeywordUtil;
import org.example.mynote.util.WordCountUtil;
import org.example.mynote.vo.AiActionVO;
import org.example.mynote.vo.AiMessageVO;
import org.example.mynote.vo.AiSessionVO;
import org.example.mynote.vo.AiStatusVO;
import org.example.mynote.vo.NoteDetailVO;
import org.example.mynote.vo.RewriteVO;
import org.example.mynote.vo.SuggestVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * AI 助手实现：对话、快捷动作、改写预览、确认后覆盖、飘窗建议。
 *
 * <p>三条设计约束：</p>
 * <ol>
 *   <li><b>改写必须先看后改。</b>rewrite 只返回预览，前端渲染 diff；只有用户点"替换"才走
 *       applyRewrite，且覆盖前写一条历史快照，可回退。</li>
 *   <li><b>上下文在本机拼装。</b>提示词由笔记正文 + 同目录摘要 + 选区组成，全部在本机完成，
 *       只有调用模型这一步把内容交给用户自己选的模型服务商。</li>
 *   <li><b>离线不是故障。</b>没配 API Key 时，飘窗建议退化为基于关键词的本地建议，
 *       背景知识与逻辑图照常可用；只有对话/改写这类必须调用模型的能力才返回 2001。</li>
 * </ol>
 *
 * @author MyNote
 */
@Service
public class AiServiceImpl implements AiService {

    private static final Logger log = LoggerFactory.getLogger(AiServiceImpl.class);

    /**
     * SSE 超时：比模型读超时多留 10 秒，避免浏览器先断
     */
    private static final long SSE_TIMEOUT_MILLIS = 130_000L;

    /**
     * 改写预览的分隔标记，提示词里要求模型用它隔开正文与说明
     */
    private static final String REWRITE_SEPARATOR = "===";

    private static final int HISTORY_TURNS = 8;

    private static final int SUGGEST_COUNT = 3;

    /**
     * 模型建议里可能夹带解释，只取 JSON 数组部分
     */
    private static final Pattern JSON_ARRAY = Pattern.compile("\\[\\s*\"(?:[^\"\\\\]|\\\\.)*\"(?:\\s*,\\s*\"(?:[^\"\\\\]|\\\\.)*\")*\\s*\\]");

    private static final List<String> OFFLINE_SCENES = Arrays.asList("knowledge", "references", "diagram",
            "suggest");

    private static final List<String> ONLINE_SCENES = Arrays.asList("chat", "polish", "expand", "shrink",
            "continue", "title", "summary", "name", "consistency", "ending");

    private final AiConfigService aiConfigService;

    private final OpenAiClient openAiClient;

    private final NoteContextBuilder contextBuilder;

    private final NoteDAO noteDAO;

    private final NoteService noteService;

    private final AiSessionDAO aiSessionDAO;

    private final AiMessageDAO aiMessageDAO;

    private final TaskExecutor taskExecutor;

    private final ObjectMapper objectMapper;

    public AiServiceImpl(AiConfigService aiConfigService, OpenAiClient openAiClient,
                         NoteContextBuilder contextBuilder, NoteDAO noteDAO, NoteService noteService,
                         AiSessionDAO aiSessionDAO, AiMessageDAO aiMessageDAO, TaskExecutor taskExecutor,
                         ObjectMapper objectMapper) {
        this.aiConfigService = aiConfigService;
        this.openAiClient = openAiClient;
        this.contextBuilder = contextBuilder;
        this.noteDAO = noteDAO;
        this.noteService = noteService;
        this.aiSessionDAO = aiSessionDAO;
        this.aiMessageDAO = aiMessageDAO;
        this.taskExecutor = taskExecutor;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiStatusVO status(Long userId) {
        AiStatusVO vo = new AiStatusVO();
        AiRuntimeConfig runtime = aiConfigService.currentRuntime(userId);
        vo.setConfigured(runtime != null);
        vo.setModel(runtime == null ? null : runtime.getModelName());
        vo.setProviderName(runtime == null ? null : runtime.getProviderName());
        vo.setOfflineScenes(OFFLINE_SCENES);
        vo.setOnlineScenes(ONLINE_SCENES);
        return vo;
    }

    @Override
    public SseEmitter chatStream(Long userId, AiChatDTO dto) {
        AiRuntimeConfig runtime = requireRuntime(userId);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        NoteContextBuilder.Context context = contextBuilder.build(userId, dto.getNoteId());
        List<OpenAiClient.Message> messages = buildChatMessages(userId, dto, context);
        String question = dto.getQuestion();
        // 会话先落库，拿到 id 后才能把助手回答写回同一条会话
        Long sessionId = ensureSession(userId, dto.getSessionId(), dto.getNoteId(), question);
        saveMessage(sessionId, userId, Constants.ROLE_USER, question, Constants.SCENE_CHAT);
        taskExecutor.execute(() -> {
            StringBuilder answer = new StringBuilder(256);
            try {
                openAiClient.chatStream(runtime, messages, delta -> {
                    answer.append(delta);
                    try {
                        emitter.send(SseEmitter.event().data(writeJson(Map.of("delta", delta))));
                    } catch (Exception e) {
                        // 客户端主动关闭页面时会抛错，这里停止发送即可，不算调用失败
                        throw new IllegalStateException("连接已断开", e);
                    }
                });
                saveMessage(sessionId, userId, Constants.ROLE_ASSISTANT, answer.toString(), Constants.SCENE_CHAT);
                Map<String, Object> done = new HashMap<>(4);
                done.put("done", true);
                done.put("sessionId", sessionId);
                done.put("model", runtime.getModelName());
                emitter.send(SseEmitter.event().data(writeJson(done)));
                emitter.complete();
            } catch (Exception e) {
                log.info("流式对话中断: {}", e.getMessage());
                if (!answer.isEmpty()) {
                    // 已经生成出来的部分对用户仍有价值，落库避免下次看不到
                    saveMessage(sessionId, userId, Constants.ROLE_ASSISTANT, answer.toString(),
                            Constants.SCENE_CHAT);
                }
                trySendError(emitter, e);
            }
        });
        return emitter;
    }

    @Override
    public AiActionVO action(Long userId, AiActionDTO dto) {
        AiRuntimeConfig runtime = requireRuntime(userId);
        NoteContextBuilder.Context context = contextBuilder.build(userId, dto.getNoteId());
        if (context.isEmpty()) {
            throw new BizException("这篇笔记还没有内容，助手无从下手");
        }
        String prompt = buildUserPrompt(context, dto.getSelection(), dto.getInstruction());
        List<OpenAiClient.Message> messages = new ArrayList<>(3);
        messages.add(new OpenAiClient.Message("system", PromptLibrary.of(dto.getAction())));
        messages.add(new OpenAiClient.Message("user", prompt));
        String text = openAiClient.chatOnce(runtime, messages);
        AiActionVO vo = new AiActionVO();
        vo.setAction(dto.getAction());
        vo.setText(text);
        vo.setModel(runtime.getModelName());
        return vo;
    }

    @Override
    public RewriteVO rewrite(Long userId, AiRewriteDTO dto) {
        AiRuntimeConfig runtime = requireRuntime(userId);
        NoteContextBuilder.Context context = contextBuilder.build(userId, dto.getNoteId());
        String action = normalizeRewriteAction(dto.getAction());
        StringBuilder prompt = new StringBuilder(512);
        prompt.append(PromptLibrary.contextBlock(context.getNoteBlock()));
        prompt.append(PromptLibrary.selectionBlock(dto.getSelection()));
        if (!context.getSiblingBlock().isEmpty()) {
            prompt.append(PromptLibrary.siblingBlock(context.getSiblingBlock()));
        }
        if (dto.getInstruction() != null && !dto.getInstruction().isBlank()) {
            prompt.append("\n作者的额外要求：").append(dto.getInstruction().trim());
        }
        List<OpenAiClient.Message> messages = new ArrayList<>(2);
        messages.add(new OpenAiClient.Message("system", PromptLibrary.of(action)));
        messages.add(new OpenAiClient.Message("user", prompt.toString()));
        String output = openAiClient.chatOnce(runtime, messages);
        RewriteVO vo = new RewriteVO();
        vo.setOriginal(dto.getSelection());
        vo.setModel(runtime.getModelName());
        List<String> sections = splitRewriteSections(output);
        if (sections.size() >= 2) {
            vo.setRevised(sections.get(0));
            vo.setExplanation(sections.get(1));
            if (sections.size() >= 3 && !isEmptyFollowUp(sections.get(2))) {
                vo.setFollowUp(sections.get(2));
            }
        } else {
            vo.setRevised(output.trim());
            vo.setExplanation("模型没有给出改动说明。");
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NoteDetailVO applyRewrite(Long userId, AiApplyDTO dto) {
        NoteDO note = noteDAO.findFirstByIdAndUserId(dto.getNoteId(), userId);
        if (note == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        String content = note.getContent() == null ? "" : note.getContent();
        int[] loc = locateOriginal(content, dto.getOriginal());
        if (loc == null) {
            throw new BizException("笔记里已经找不到这段原文了（可能你刚改过它），"
                    + "请把新文本手动粘贴过去，助手不做盲覆盖");
        }
        String replaced = content.substring(0, loc[0]) + dto.getRevised()
                + content.substring(loc[0] + loc[1]);
        NoteUpdateDTO update = new NoteUpdateDTO();
        update.setContent(replaced);
        update.setSnapshot(true);
        update.setSnapshotSource(Constants.HISTORY_SOURCE_AI);
        NoteDetailVO detail = noteService.update(note.getId(), update);
        log.info("AI 改写已覆盖 noteId={}，原字数={} 新字数={}", note.getId(), note.getWordCount(),
                detail.getWordCount());
        return detail;
    }

    @Override
    public SuggestVO suggest(Long userId, AiSuggestDTO dto) {
        SuggestVO vo = new SuggestVO();
        NoteContextBuilder.Context context = contextBuilder.build(userId, dto.getNoteId());
        vo.setMode(context.getMode());
        AiRuntimeConfig runtime = aiConfigService.currentRuntime(userId);
        if (runtime != null && !context.isEmpty()) {
            try {
                List<OpenAiClient.Message> messages = new ArrayList<>(2);
                messages.add(new OpenAiClient.Message("system", PromptLibrary.SCENE_SUGGEST));
                StringBuilder userPrompt = new StringBuilder(512);
                userPrompt.append(PromptLibrary.contextBlock(context.getNoteBlock()));
                userPrompt.append(dto.getSelection() == null ? ""
                        : PromptLibrary.selectionBlock(dto.getSelection()));
                if (dto.getPrevious() != null && !dto.getPrevious().isEmpty()) {
                    userPrompt.append("\n\n上一轮已经给过这些问题，请换一批、避免相同或近似：\n")
                            .append(String.join("；", dto.getPrevious()));
                }
                messages.add(new OpenAiClient.Message("user", userPrompt.toString()));
                String raw = openAiClient.chatOnce(runtime, messages);
                List<String> parsed = parseStringArray(raw);
                if (!parsed.isEmpty()) {
                    vo.setSuggestions(parsed);
                    vo.setSource("ai");
                    return vo;
                }
            } catch (Exception e) {
                log.info("飘窗建议改用本地规则: {}", e.getMessage());
            }
        }
        vo.setSuggestions(localSuggestions(context, dto.getSelection()));
        vo.setSource("local");
        return vo;
    }

    @Override
    public List<AiSessionVO> sessions(Long userId, Long noteId) {
        List<AiSessionDO> list = noteId == null
                ? aiSessionDAO.listByUser(userId, org.springframework.data.domain.PageRequest.of(0, 30))
                : aiSessionDAO.findByUserIdAndNoteIdOrderByIdDesc(userId, noteId);
        return list.stream().map(this::toSessionVO).collect(Collectors.toList());
    }

    @Override
    public List<AiMessageVO> messages(Long userId, Long sessionId) {
        AiSessionDO session = aiSessionDAO.findFirstByIdAndUserId(sessionId, userId);
        if (session == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return aiMessageDAO.findBySessionIdOrderByIdAsc(sessionId).stream()
                .map(item -> {
                    AiMessageVO vo = new AiMessageVO();
                    vo.setId(item.getId());
                    vo.setRole(item.getRole());
                    vo.setContent(item.getContent());
                    vo.setScene(item.getScene());
                    vo.setCreateTime(item.getCreateTime());
                    return vo;
                }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(Long userId, Long sessionId) {
        AiSessionDO session = aiSessionDAO.findFirstByIdAndUserId(sessionId, userId);
        if (session == null) {
            return;
        }
        for (AiMessageDO message : aiMessageDAO.findBySessionIdOrderByIdAsc(sessionId)) {
            aiMessageDAO.deleteById(message.getId());
        }
        aiSessionDAO.deleteById(sessionId);
    }

    /**
     * 组对话消息：系统提示 + 最近若干轮历史 + 本轮问题（带上下文）。
     *
     * @param userId  用户 id
     * @param dto     入参
     * @param context 笔记上下文
     * @return 消息列表
     */
    private List<OpenAiClient.Message> buildChatMessages(Long userId, AiChatDTO dto,
                                                         NoteContextBuilder.Context context) {
        List<OpenAiClient.Message> messages = new ArrayList<>(HISTORY_TURNS + 3);
        messages.add(new OpenAiClient.Message("system", PromptLibrary.SCENE_CHAT));
        if (dto.getSessionId() != null) {
            AiSessionDO session = aiSessionDAO.findFirstByIdAndUserId(dto.getSessionId(), userId);
            if (session != null) {
                List<AiMessageDO> history = aiMessageDAO.findBySessionIdOrderByIdAsc(session.getId());
                int from = Math.max(0, history.size() - HISTORY_TURNS);
                for (AiMessageDO item : history.subList(from, history.size())) {
                    messages.add(new OpenAiClient.Message(item.getRole(), item.getContent()));
                }
            }
        }
        StringBuilder userPrompt = new StringBuilder(600);
        if (!context.isEmpty()) {
            userPrompt.append(PromptLibrary.contextBlock(context.getNoteBlock()));
            if (!context.getSiblingBlock().isEmpty()) {
                userPrompt.append(PromptLibrary.siblingBlock(context.getSiblingBlock()));
            }
            userPrompt.append('\n');
        }
        userPrompt.append("我的问题：").append(dto.getQuestion());
        messages.add(new OpenAiClient.Message("user", userPrompt.toString()));
        return messages;
    }

    /**
     * 组装动作类请求的用户消息。
     *
     * @param context     上下文
     * @param selection   选区
     * @param instruction 附加要求
     * @return 提示文本
     */
    private String buildUserPrompt(NoteContextBuilder.Context context, String selection, String instruction) {
        StringBuilder sb = new StringBuilder(600);
        sb.append(PromptLibrary.contextBlock(context.getNoteBlock()));
        if (selection != null && !selection.isBlank()) {
            sb.append(PromptLibrary.selectionBlock(selection));
        }
        if (!context.getSiblingBlock().isEmpty()) {
            sb.append(PromptLibrary.siblingBlock(context.getSiblingBlock()));
        }
        if (instruction != null && !instruction.isBlank()) {
            sb.append("\n作者的额外要求：").append(instruction.trim());
        }
        return sb.toString();
    }

    /**
     * 本地飘窗建议：按模式给"此刻真的能问出口"的问题。
     *
     * @param context   上下文
     * @param selection 选区
     * @return 建议
     */
    private List<String> localSuggestions(NoteContextBuilder.Context context, String selection) {
        List<String> result = new ArrayList<>(SUGGEST_COUNT);
        String haystack = (selection == null ? "" : selection) + " " + context.getNoteBlock();
        List<String> words = KeywordUtil.extract(haystack, 3);
        String word = words.isEmpty() ? "这段" : words.get(0);
        String second = words.size() > 1 ? words.get(1) : null;
        switch (context.getMode()) {
            case "research":
                result.add("「" + word + "」有哪些代表性工作？");
                result.add(second == null ? "帮我把这段整理成综述提纲" : "「" + word + "」和「" + second + "」是什么关系？");
                result.add("这段的方法描述够严谨吗？");
                break;
            case "creation":
            case "life":
                result.add("「" + word + "」那个年代具体是怎么过的？");
                result.add("这段的感官细节够不够？");
                result.add("帮我看看人物反应是否符合设定");
                break;
            default:
                result.add("给这段拟三个标题");
                result.add("把这段压缩三成");
                result.add("接着往下写三个走向");
                break;
        }
        return result;
    }

    /**
     * 取会话：入参没带就新建，标题用首问前 20 字。
     *
     * @param userId    用户 id
     * @param sessionId 已有会话 id
     * @param noteId    绑定笔记
     * @param question  首问
     * @return 会话 id
     */
    private Long ensureSession(Long userId, Long sessionId, Long noteId, String question) {
        if (sessionId != null) {
            AiSessionDO exists = aiSessionDAO.findFirstByIdAndUserId(sessionId, userId);
            if (exists != null) {
                return exists.getId();
            }
        }
        AiSessionDO session = new AiSessionDO();
        session.setUserId(userId);
        session.setNoteId(noteId == null ? 0L : noteId);
        session.setTitle(WordCountUtil.truncate(question, 20));
        session.setMessageCount(0);
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());
        return aiSessionDAO.save(session).getId();
    }

    /**
     * 写一条消息并维护会话计数。
     *
     * @param sessionId 会话 id
     * @param userId    用户 id
     * @param role      角色
     * @param content   内容
     * @param scene     场景
     */
    private void saveMessage(Long sessionId, Long userId, String role, String content, String scene) {
        AiMessageDO message = new AiMessageDO();
        message.setSessionId(sessionId);
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setScene(scene);
        message.setCreateTime(LocalDateTime.now());
        aiMessageDAO.save(message);
        aiSessionDAO.findById(sessionId).ifPresent(session -> {
            session.setMessageCount(aiMessageDAO.findBySessionIdOrderByIdAsc(sessionId).size());
            session.setUpdateTime(LocalDateTime.now());
            aiSessionDAO.save(session);
        });
    }

    /**
     * 取当前用户的可用模型配置，未配置时抛出引导性提示。
     *
     * @param userId 用户 id
     * @return 运行时配置
     */
    private AiRuntimeConfig requireRuntime(Long userId) {
        AiRuntimeConfig runtime = aiConfigService.currentRuntime(userId);
        if (runtime == null) {
            throw new BizException(ResultCode.AI_NOT_CONFIGURED);
        }
        return runtime;
    }

    /**
     * 解析模型返回的字符串数组，容忍夹带的多余文字。
     *
     * @param raw 模型输出
     * @return 列表
     */
    private List<String> parseStringArray(String raw) {
        if (raw == null) {
            return List.of();
        }
        Matcher matcher = JSON_ARRAY.matcher(raw);
        if (!matcher.find()) {
            return List.of();
        }
        try {
            String[] arr = objectMapper.readValue(matcher.group(), String[].class);
            List<String> result = new ArrayList<>(arr.length);
            for (String item : arr) {
                if (item != null && !item.isBlank()) {
                    result.add(item.trim());
                }
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 按分隔行把模型输出切成若干段：正文 / 说明 / 追问。
     * 分隔行只要包含连续三个等号就算（模型常写成 === 、=== 说明 === 、=== 追问 === 等），
     * 避免这些标记混进正文被前端标成"新增"、或点覆盖时被写进原文。
     *
     * @param output 模型输出
     * @return 非空的段落列表，通常为 2~3 段
     */
    private List<String> splitRewriteSections(String output) {
        List<String> sections = new ArrayList<>();
        if (output == null) {
            return sections;
        }
        String[] lines = output.replace("\r\n", "\n").split("\n", -1);
        StringBuilder current = new StringBuilder();
        for (String line : lines) {
            if (isSeparatorLine(line)) {
                String section = current.toString().trim();
                if (!section.isEmpty()) {
                    sections.add(section);
                }
                current.setLength(0);
            } else {
                if (current.length() > 0) {
                    current.append('\n');
                }
                current.append(line);
            }
        }
        String last = current.toString().trim();
        if (!last.isEmpty()) {
            sections.add(last);
        }
        return sections;
    }

    /**
     * 是否为分隔行：整行里包含连续三个等号即视为分隔，兼容模型加"说明/追问"标签的写法。
     */
    private boolean isSeparatorLine(String line) {
        return line != null && line.contains(REWRITE_SEPARATOR);
    }

    /**
     * 模型用"无/没有/暂无"表示本轮没有可再追问的方向，视为空。
     */
    private boolean isEmptyFollowUp(String followUp) {
        if (followUp == null) {
            return true;
        }
        String t = followUp.strip();
        return t.isEmpty() || t.equals("无") || t.equals("没有") || t.equals("暂无") || t.equals("不用");
    }

    /**
     * 在正文里定位要覆盖的原文，返回 int[]{起始下标, 匹配长度}，找不到返回 null。
     *
     * <p>Vditor 的选区（getSelection）与落库 Markdown（getValue）在首尾空白、换行符、
     * 连续空白上可能不完全一致，逐级放宽匹配，避免"原文没动却找不到"。</p>
     */
    private int[] locateOriginal(String content, String original) {
        if (content == null || original == null || original.isEmpty()) {
            return null;
        }
        // 1) 精确匹配
        int at = content.indexOf(original);
        if (at >= 0) {
            return new int[]{at, original.length()};
        }
        // 2) 去掉选区首尾空白后匹配（编辑器选区常多带首尾空格/换行）
        String trimmed = original.strip();
        if (!trimmed.isEmpty() && !trimmed.equals(original)) {
            at = content.indexOf(trimmed);
            if (at >= 0) {
                return new int[]{at, trimmed.length()};
            }
        }
        // 3) 统一换行符后匹配（\r\n / \r -> \n）
        String c = content.replace("\r\n", "\n").replace('\r', '\n');
        String o = original.replace("\r\n", "\n").replace('\r', '\n');
        if (!c.equals(content) || !o.equals(original)) {
            at = c.indexOf(o);
            if (at >= 0) {
                return new int[]{at, o.length()};
            }
        }
        // 4) 折叠连续空白后模糊匹配，并把命中位置映射回原 content
        return fuzzyWhitespaceMatch(content, original);
    }

    private int[] fuzzyWhitespaceMatch(String content, String original) {
        String cn = collapseWhitespace(content);
        String on = collapseWhitespace(original);
        if (on.isEmpty()) {
            return null;
        }
        int normAt = cn.indexOf(on);
        if (normAt < 0) {
            return null;
        }
        int origStart = mapIndex(content, cn, normAt);
        int origEnd = mapIndex(content, cn, normAt + on.length());
        if (origStart < 0 || origEnd <= origStart) {
            return null;
        }
        return new int[]{origStart, origEnd - origStart};
    }

    private static String collapseWhitespace(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        boolean inSpace = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (Character.isWhitespace(ch)) {
                if (!inSpace) {
                    sb.append(' ');
                    inSpace = true;
                }
            } else {
                sb.append(ch);
                inSpace = false;
            }
        }
        return sb.toString();
    }

    /**
     * 把"折叠后"字符串里的位置映射回原字符串下标（折叠规则与 collapseWhitespace 一致）。
     */
    private static int mapIndex(String original, String collapsed, int collapsedIndex) {
        int ci = 0;
        int oi = 0;
        boolean prevSpace = false;
        while (ci < collapsedIndex && oi < original.length()) {
            char oc = original.charAt(oi);
            if (Character.isWhitespace(oc)) {
                if (!prevSpace) {
                    ci++;
                }
                prevSpace = true;
            } else {
                ci++;
                prevSpace = false;
            }
            oi++;
        }
        return oi;
    }

    private String normalizeRewriteAction(String action) {
        if (action == null) {
            return "polish";
        }
        String lower = action.toLowerCase();
        switch (lower) {
            case "expand":
                return "expand";
            case "shrink":
                return "shrink";
            default:
                return "polish";
        }
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "{\"delta\":\"\"}";
        }
    }

    /**
     * 尽力通知前端错误帧；连接已断时忽略即可。
     *
     * @param emitter 发射器
     * @param e       异常
     */
    private void trySendError(SseEmitter emitter, Exception e) {
        try {
            int code = e instanceof BizException ? ((BizException) e).getCode()
                    : ResultCode.AI_CALL_FAILED.getCode();
            String message = e instanceof BizException ? e.getMessage() : ResultCode.AI_CALL_FAILED.getMessage();
            Map<String, Object> payload = new HashMap<>(4);
            payload.put("error", true);
            payload.put("code", code);
            payload.put("message", message);
            emitter.send(SseEmitter.event().data(writeJson(payload)));
            emitter.complete();
        } catch (Exception ignored) {
            log.debug("SSE 错误帧发送失败，客户端可能已关闭");
        }
    }

    private AiSessionVO toSessionVO(AiSessionDO session) {
        AiSessionVO vo = new AiSessionVO();
        vo.setId(session.getId());
        vo.setNoteId(session.getNoteId());
        vo.setTitle(session.getTitle());
        vo.setMessageCount(session.getMessageCount());
        vo.setUpdateTime(session.getUpdateTime());
        return vo;
    }

}
