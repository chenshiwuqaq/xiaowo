package org.example.mynote.controller;

import jakarta.validation.Valid;
import org.example.mynote.common.Result;
import org.example.mynote.common.UserContext;
import org.example.mynote.dto.AiActionDTO;
import org.example.mynote.dto.AiApplyDTO;
import org.example.mynote.dto.AiBindRefDTO;
import org.example.mynote.dto.AiChatDTO;
import org.example.mynote.dto.AiRewriteDTO;
import org.example.mynote.dto.AiSuggestDTO;
import org.example.mynote.dto.DiagramGenerateDTO;
import org.example.mynote.dto.DiagramSaveDTO;
import org.example.mynote.service.AiService;
import org.example.mynote.service.DiagramService;
import org.example.mynote.service.KnowledgeService;
import org.example.mynote.service.ScholarService;
import org.example.mynote.vo.AiActionVO;
import org.example.mynote.vo.AiMessageVO;
import org.example.mynote.vo.AiSessionVO;
import org.example.mynote.vo.AiStatusVO;
import org.example.mynote.vo.DiagramVO;
import org.example.mynote.vo.KnowledgeResultVO;
import org.example.mynote.vo.NoteDetailVO;
import org.example.mynote.vo.ReferenceResultVO;
import org.example.mynote.vo.RewriteVO;
import org.example.mynote.vo.SuggestVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * AI 助手接口。
 *
 * <p>面板上的每一个按钮对应这里的一个入口：对话走 SSE 流式，改写走"预览 + 确认覆盖"两步，
 * 背景知识与文献推荐即使没有 API Key 也必须返回内容。</p>
 *
 * @author MyNote
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiService aiService;

    private final KnowledgeService knowledgeService;

    private final ScholarService scholarService;

    private final DiagramService diagramService;

    public AiController(AiService aiService, KnowledgeService knowledgeService, ScholarService scholarService,
                        DiagramService diagramService) {
        this.aiService = aiService;
        this.knowledgeService = knowledgeService;
        this.scholarService = scholarService;
        this.diagramService = diagramService;
    }

    /**
     * 助手可用状态。
     *
     * @return 状态
     */
    @GetMapping("/status")
    public Result<AiStatusVO> status() {
        return Result.ok(aiService.status(UserContext.require()));
    }

    /**
     * 流式对话。
     *
     * @param dto 入参
     * @return SSE 发射器
     */
    @PostMapping("/chat/stream")
    public SseEmitter chatStream(@Valid @RequestBody AiChatDTO dto) {
        return aiService.chatStream(UserContext.require(), dto);
    }

    /**
     * 快捷写作动作。
     *
     * @param dto 入参
     * @return 动作结果
     */
    @PostMapping("/action")
    public Result<AiActionVO> action(@Valid @RequestBody AiActionDTO dto) {
        return Result.ok(aiService.action(UserContext.require(), dto));
    }

    /**
     * 改写预览，不落库。
     *
     * @param dto 入参
     * @return 预览
     */
    @PostMapping("/rewrite")
    public Result<RewriteVO> rewrite(@Valid @RequestBody AiRewriteDTO dto) {
        return Result.ok(aiService.rewrite(UserContext.require(), dto));
    }

    /**
     * 用户确认预览后覆盖正文。
     *
     * @param dto 入参
     * @return 更新后的笔记
     */
    @PostMapping("/apply-rewrite")
    public Result<NoteDetailVO> applyRewrite(@Valid @RequestBody AiApplyDTO dto) {
        return Result.ok(aiService.applyRewrite(UserContext.require(), dto));
    }

    /**
     * 飘窗建议。
     *
     * @param dto 入参
     * @return 建议
     */
    @PostMapping("/suggest")
    public Result<SuggestVO> suggest(@Valid @RequestBody AiSuggestDTO dto) {
        return Result.ok(aiService.suggest(UserContext.require(), dto));
    }

    /**
     * 背景知识浮现（生活 / 小说分支）。
     *
     * @param noteId    笔记 id
     * @param selection 选区文本，可空
     * @return 命中结果
     */
    @GetMapping("/knowledge")
    public Result<KnowledgeResultVO> knowledge(@RequestParam(required = false) Long noteId,
                                               @RequestParam(required = false) String selection) {
        return Result.ok(knowledgeService.surface(UserContext.require(), noteId, selection));
    }

    /**
     * 采纳一张背景知识卡片。
     *
     * @param cardId 卡片 id
     * @return 空
     */
    @PostMapping("/knowledge/adopt")
    public Result<Void> adopt(@RequestParam Long cardId) {
        knowledgeService.adopt(cardId);
        return Result.ok();
    }

    /**
     * 参考文献推荐（科研分支）。
     *
     * @param noteId 笔记 id
     * @return 推荐结果
     */
    @GetMapping("/references")
    public Result<ReferenceResultVO> references(@RequestParam Long noteId) {
        return Result.ok(scholarService.recommend(UserContext.require(), noteId));
    }

    /**
     * 绑定文献到笔记。
     *
     * @param dto 入参
     * @return 空
     */
    @PostMapping("/references/bind")
    public Result<Void> bind(@Valid @RequestBody AiBindRefDTO dto) {
        scholarService.bind(UserContext.require(), dto.getNoteId(), dto.getRefId(),
                dto.getSource() == null ? "ai" : dto.getSource());
        return Result.ok();
    }

    /**
     * 解除文献绑定。
     *
     * @param noteId 笔记 id
     * @param refId  文献 id
     * @return 空
     */
    @DeleteMapping("/references/bind")
    public Result<Void> unbind(@RequestParam Long noteId, @RequestParam Long refId) {
        scholarService.unbind(UserContext.require(), noteId, refId);
        return Result.ok();
    }

    /**
     * 会话列表。
     *
     * @param noteId 笔记 id，可空
     * @return 会话
     */
    @GetMapping("/sessions")
    public Result<List<AiSessionVO>> sessions(@RequestParam(required = false) Long noteId) {
        return Result.ok(aiService.sessions(UserContext.require(), noteId));
    }

    /**
     * 会话消息。
     *
     * @param id 会话 id
     * @return 消息
     */
    @GetMapping("/session/{id}/messages")
    public Result<List<AiMessageVO>> messages(@PathVariable Long id) {
        return Result.ok(aiService.messages(UserContext.require(), id));
    }

    /**
     * 删除会话。
     *
     * @param id 会话 id
     * @return 空
     */
    @DeleteMapping("/session/{id}")
    public Result<Void> deleteSession(@PathVariable Long id) {
        aiService.deleteSession(UserContext.require(), id);
        return Result.ok();
    }

    /**
     * 由多篇笔记生成逻辑图。
     *
     * @param dto 入参
     * @return 生成的图
     */
    @PostMapping("/diagram")
    public Result<DiagramVO> generateDiagram(@Valid @RequestBody DiagramGenerateDTO dto) {
        return Result.ok(diagramService.generate(UserContext.require(), dto));
    }

    /**
     * 保存逻辑图。
     *
     * @param dto 入参
     * @return 落库结果
     */
    @PostMapping("/diagram/save")
    public Result<DiagramVO> saveDiagram(@Valid @RequestBody DiagramSaveDTO dto) {
        return Result.ok(diagramService.save(UserContext.require(), dto));
    }
}
