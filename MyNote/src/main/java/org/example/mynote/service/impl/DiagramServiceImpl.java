package org.example.mynote.service.impl;

import org.example.mynote.ai.AiRuntimeConfig;
import org.example.mynote.ai.OpenAiClient;
import org.example.mynote.ai.PromptLibrary;
import org.example.mynote.common.BizException;
import org.example.mynote.common.ResultCode;
import org.example.mynote.dao.DiagramDAO;
import org.example.mynote.dao.NoteDAO;
import org.example.mynote.dto.DiagramGenerateDTO;
import org.example.mynote.dto.DiagramSaveDTO;
import org.example.mynote.entity.AiConfigDO;
import org.example.mynote.entity.DiagramDO;
import org.example.mynote.entity.NoteDO;
import org.example.mynote.service.AiConfigService;
import org.example.mynote.service.DiagramService;
import org.example.mynote.util.KeywordUtil;
import org.example.mynote.util.WordCountUtil;
import org.example.mynote.vo.DiagramVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 逻辑图生成实现。
 *
 * <p>"多篇笔记画一张图"有两种价值：一是<b>结构可视化</b>（本地算法，稳定、离线可用），
 * 二是<b>关系再发现</b>（模型重排，能看出人眼容易漏掉的依赖关系）。所以这里两条路都留着：</p>
 * <ul>
 *   <li>本地算法：以笔记标题为节点，用共享关键词建边（mindmap 按标题层级展开，
 *       flowchart/graph 按关键词共现连边，timeline 按更新时间排列）；</li>
 *   <li>模型路径：把标题+摘要交给模型重排结构，输出前做净化（剥围栏、限行数、
 *       标签加引号），解析不出合法 Mermaid 时自动退回本地结果，绝不让前端拿到画不出来的代码。</li>
 * </ul>
 *
 * @author MyNote
 */
@Service
public class DiagramServiceImpl implements DiagramService {

    private static final Logger log = LoggerFactory.getLogger(DiagramServiceImpl.class);

    private static final int MIN_NOTES = 2;

    private static final int MAX_NODES = 18;

    private static final int MAX_LINES = 60;

    private static final int LABEL_CHARS = 14;

    private static final int KEYWORDS_PER_NOTE = 3;

    /**
     * 共现边权重阈值：低于该值的两个笔记不连线，避免一张全是线的毛球
     */
    private static final int EDGE_WEIGHT = 2;

    private static final Set<String> ALLOWED_HEADS = Set.of("flowchart", "graph", "mindmap", "timeline");

    private final DiagramDAO diagramDAO;

    private final NoteDAO noteDAO;

    private final AiConfigService aiConfigService;

    private final OpenAiClient openAiClient;

    public DiagramServiceImpl(DiagramDAO diagramDAO, NoteDAO noteDAO, AiConfigService aiConfigService,
                              OpenAiClient openAiClient) {
        this.diagramDAO = diagramDAO;
        this.noteDAO = noteDAO;
        this.aiConfigService = aiConfigService;
        this.openAiClient = openAiClient;
    }

    @Override
    public DiagramVO generate(Long userId, DiagramGenerateDTO dto) {
        List<Long> ids = dto.getNoteIds() == null ? List.of() : dto.getNoteIds().stream().distinct().toList();
        if (ids.size() < MIN_NOTES) {
            throw new BizException("至少勾选两篇笔记，一张图才有关系可画");
        }
        List<NoteDO> notes = noteDAO.findByIdInAndUserIdAndDeletedFalse(new ArrayList<>(ids), userId);
        if (notes.size() < MIN_NOTES) {
            throw new BizException("选中的笔记不足两篇（可能有的已被删除）");
        }
        notes.sort(Comparator.comparing(NoteDO::getUpdateTime,
                Comparator.nullsLast(Comparator.reverseOrder())));
        String type = normalizeType(dto.getType());
        String localCode = renderLocal(notes, type);
        String generator = "local";
        String code = localCode;
        if (Boolean.TRUE.equals(dto.getUseAi())) {
            String aiCode = renderByModel(userId, notes, type);
            if (aiCode != null) {
                code = aiCode;
                generator = "ai";
            }
        }
        DiagramVO vo = new DiagramVO();
        vo.setTitle(defaultTitle(notes));
        vo.setDiagramType(type);
        vo.setNoteIds(ids.stream().map(String::valueOf).collect(Collectors.joining(",")));
        vo.setCode(code);
        vo.setGenerator(generator);
        vo.setCreateTime(LocalDateTime.now());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DiagramVO save(Long userId, DiagramSaveDTO dto) {
        DiagramDO entity = new DiagramDO();
        entity.setUserId(userId);
        entity.setTitle(dto.getTitle().trim());
        entity.setDiagramType(normalizeType(dto.getDiagramType()));
        entity.setNoteIds(dto.getNoteIds());
        entity.setCode(requireRenderable(dto.getCode()));
        entity.setCreateTime(LocalDateTime.now());
        return toVO(diagramDAO.save(entity), "local");
    }

    @Override
    public List<DiagramVO> list(Long userId) {
        return diagramDAO.findByUserIdOrderByCreateTimeDesc(userId).stream()
                .map(item -> toVO(item, null)).collect(Collectors.toList());
    }

    @Override
    public DiagramVO detail(Long userId, Long id) {
        DiagramDO entity = diagramDAO.findFirstByIdAndUserId(id, userId);
        if (entity == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return toVO(entity, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long id) {
        DiagramDO entity = diagramDAO.findFirstByIdAndUserId(id, userId);
        if (entity == null) {
            return;
        }
        diagramDAO.deleteById(id);
    }

    /**
     * 本地算法生成 Mermaid。
     *
     * @param notes 笔记
     * @param type  图类型
     * @return 源码
     */
    private String renderLocal(List<NoteDO> notes, String type) {
        List<NoteDO> limited = notes.size() > MAX_NODES ? notes.subList(0, MAX_NODES) : notes;
        Map<Long, List<String>> keywordMap = new LinkedHashMap<>();
        for (NoteDO note : limited) {
            List<String> words = new ArrayList<>(KeywordUtil.extract(
                    WordCountUtil.stripMarkdown(note.getContent()), KEYWORDS_PER_NOTE));
            keywordMap.put(note.getId(), words);
        }
        if ("mindmap".equals(type)) {
            return renderMindmap(limited, keywordMap);
        }
        if ("timeline".equals(type)) {
            return renderTimeline(limited);
        }
        return renderGraph(limited, keywordMap, "flowchart LR");
    }

    /**
     * 思维导图：主题取第一篇笔记标题的关键词，其余笔记及其关键词作为分支。
     *
     * @param notes      笔记
     * @param keywordMap 关键词表
     * @return Mermaid mindmap
     */
    private String renderMindmap(List<NoteDO> notes, Map<Long, List<String>> keywordMap) {
        String root = label(notes.get(0).getTitle());
        StringBuilder sb = new StringBuilder("mindmap\n  root((").append(root).append("))\n");
        for (NoteDO note : notes) {
            sb.append("    ").append(label(note.getTitle())).append('\n');
            for (String word : keywordMap.getOrDefault(note.getId(), List.of())) {
                sb.append("      ").append(label(word)).append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * 时间线：按更新时间排列，能看出写作节奏与主题漂移。
     *
     * @param notes 笔记
     * @return Mermaid timeline
     */
    private String renderTimeline(List<NoteDO> notes) {
        StringBuilder sb = new StringBuilder("timeline\n    title 写作轨迹\n");
        for (NoteDO note : notes) {
            String day = note.getUpdateTime() == null ? "未定"
                    : note.getUpdateTime().toLocalDate().toString();
            sb.append("    ").append(day).append(" : ").append(label(note.getTitle())).append('\n');
        }
        return sb.toString();
    }

    /**
     * 关系图：共现关键词建边，边上标出共享的词。
     *
     * @param notes      笔记
     * @param keywordMap 关键词表
     * @param head       图头（flowchart LR）
     * @return Mermaid flowchart
     */
    private String renderGraph(List<NoteDO> notes, Map<Long, List<String>> keywordMap, String head) {
        StringBuilder sb = new StringBuilder(head).append('\n');
        Map<Long, String> ids = new LinkedHashMap<>();
        int index = 0;
        for (NoteDO note : notes) {
            String nodeId = "N" + (++index);
            ids.put(note.getId(), nodeId);
            sb.append("    ").append(nodeId).append("[\"").append(label(note.getTitle())).append("\"]\n");
        }
        for (int i = 0; i < notes.size(); i++) {
            for (int j = i + 1; j < notes.size(); j++) {
                Set<String> shared = sharedWords(keywordMap.get(notes.get(i).getId()),
                        keywordMap.get(notes.get(j).getId()));
                if (shared.size() < EDGE_WEIGHT) {
                    continue;
                }
                sb.append("    ").append(ids.get(notes.get(i).getId())).append(" -->|")
                        .append(label(String.join("、", shared))).append("| ")
                        .append(ids.get(notes.get(j).getId())).append('\n');
            }
        }
        // 没有任何共现边时退化为按时间串联，至少能看出先后关系，不至于给出一堆孤立点
        if (!sb.toString().contains("-->")) {
            for (int i = 0; i + 1 < notes.size(); i++) {
                sb.append("    ").append(ids.get(notes.get(i).getId())).append(" -.-> ")
                        .append(ids.get(notes.get(i + 1).getId())).append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * 交模型重排结构，输出净化后返回；不可用时返回 null 由调用方回落本地结果。
     *
     * @param userId 用户 id
     * @param notes  笔记
     * @param type   类型
     * @return 净化后的源码或 null
     */
    private String renderByModel(Long userId, List<NoteDO> notes, String type) {
        AiRuntimeConfig runtime = aiConfigService.currentRuntime(userId);
        if (runtime == null) {
            return null;
        }
        StringBuilder material = new StringBuilder(512);
        for (NoteDO note : notes) {
            material.append("- 《").append(note.getTitle()).append("》：")
                    .append(WordCountUtil.truncate(
                            note.getSummary() == null ? WordCountUtil.summarize(note.getContent(), 80)
                                    : note.getSummary(), 80)).append('\n');
        }
        try {
            List<OpenAiClient.Message> messages = new ArrayList<>(2);
            messages.add(new OpenAiClient.Message("system", PromptLibrary.SCENE_DIAGRAM));
            messages.add(new OpenAiClient.Message("user", "请为下面这些笔记画一张" + type + "图：\n" + material));
            String raw = openAiClient.chatOnce(runtime, messages);
            return sanitize(raw, type);
        } catch (Exception e) {
            log.info("逻辑图改用本地算法生成: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 净化模型输出的 Mermaid：剥掉代码围栏、限制行数、校验首行关键字。
     *
     * @param raw  模型输出
     * @param type 期望类型
     * @return 可渲染源码，不合格返回 null
     */
    private String sanitize(String raw, String type) {
        if (raw == null) {
            return null;
        }
        String text = raw.replace("\r", "").trim();
        text = text.replaceAll("^```[a-zA-Z]*", "").replaceAll("```$", "").trim();
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            if (line.startsWith("graph") || line.startsWith("subgraph")) {
                line = "flowchart LR";
            }
            lines.add(line.length() > 200 ? line.substring(0, 200) : line);
            if (lines.size() >= MAX_LINES) {
                break;
            }
        }
        if (lines.isEmpty()) {
            return null;
        }
        String head = lines.get(0).trim().split("\\s+")[0];
        if (!ALLOWED_HEADS.contains(head)) {
            return null;
        }
        return String.join("\n", lines);
    }

    /**
     * 保存前的兜底校验，避免把无法渲染的图存进库。
     *
     * @param code 源码
     * @return 校验后的源码
     */
    private String requireRenderable(String code) {
        String cleaned = sanitize(code, "flowchart");
        if (cleaned == null) {
            throw new BizException("图代码首行需要是 flowchart / mindmap / timeline / graph");
        }
        return cleaned;
    }

    private Set<String> sharedWords(List<String> a, List<String> b) {
        if (a == null || b == null) {
            return Set.of();
        }
        Set<String> shared = new LinkedHashSet<>(a);
        shared.retainAll(b);
        return shared;
    }

    /**
     * 节点标签：截断 + 去掉会破坏 Mermaid 语法的字符。
     *
     * @param text 原文
     * @return 安全标签
     */
    private String label(String text) {
        if (text == null || text.isBlank()) {
            return "未命名";
        }
        String clean = text.replace("\"", "'").replace("[", "〔").replace("]", "〕")
                .replace("{", "「").replace("}", "」").replace("(", "（").replace(")", "）")
                .replace("<", "＜").replace(">", "＞").replace("|", "｜").trim();
        clean = clean.replaceAll("[#*`>]", "");
        return clean.length() > LABEL_CHARS ? clean.substring(0, LABEL_CHARS) : clean;
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return "flowchart";
        }
        String lower = type.trim().toLowerCase();
        switch (lower) {
            case "mindmap":
            case "timeline":
            case "graph":
            case "flowchart":
                return lower;
            default:
                return "flowchart";
        }
    }

    private String defaultTitle(List<NoteDO> notes) {
        String first = label(notes.get(0).getTitle());
        return notes.size() == 2 ? first + " 与 " + label(notes.get(1).getTitle()) + " 的关系"
                : first + " 等 " + notes.size() + " 篇的逻辑图";
    }

    private DiagramVO toVO(DiagramDO entity, String generator) {
        DiagramVO vo = new DiagramVO();
        vo.setId(entity.getId());
        vo.setTitle(entity.getTitle());
        vo.setDiagramType(entity.getDiagramType());
        vo.setNoteIds(entity.getNoteIds());
        vo.setCode(entity.getCode());
        vo.setGenerator(generator == null ? "saved" : generator);
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}
