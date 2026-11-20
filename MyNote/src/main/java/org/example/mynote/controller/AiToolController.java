package org.example.mynote.controller;

import jakarta.validation.Valid;
import org.example.mynote.common.Result;
import org.example.mynote.common.UserContext;
import org.example.mynote.dto.AiToolRunDTO;
import org.example.mynote.dto.AiToolSaveDTO;
import org.example.mynote.plugin.AiToolRegistry;
import org.example.mynote.service.AiToolService;
import org.example.mynote.vo.AiToolDetailVO;
import org.example.mynote.vo.AiToolRunVO;
import org.example.mynote.vo.AiToolVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 工具插件接口。
 *
 * <p>分两半：面板侧取「当前可用插件清单」并通用执行；管理侧维护账号自己的声明式插件。
 * 旧的 {@code /api/ai/knowledge}、{@code /api/ai/references} 仍保留，内部与这里共用同一套服务。</p>
 *
 * @author MyNote
 */
@RestController
@RequestMapping("/api/ai/tools")
public class AiToolController {

    private final AiToolRegistry registry;

    private final AiToolService aiToolService;

    public AiToolController(AiToolRegistry registry, AiToolService aiToolService) {
        this.registry = registry;
        this.aiToolService = aiToolService;
    }

    /**
     * 当前可用插件清单（内置 + 已启用的用户插件）。
     *
     * @return 清单
     */
    @GetMapping
    public Result<List<AiToolVO>> list() {
        return Result.ok(registry.listActive(UserContext.require()));
    }

    /**
     * 通用执行。
     *
     * @param dto 入参
     * @return 结果
     */
    @PostMapping("/run")
    public Result<AiToolRunVO> run(@Valid @RequestBody AiToolRunDTO dto) {
        return Result.ok(registry.run(UserContext.require(), dto));
    }

    /**
     * 我的插件列表（管理页，含停用）。
     *
     * @return 列表
     */
    @GetMapping("/mine")
    public Result<List<AiToolDetailVO>> mine() {
        return Result.ok(aiToolService.mine(UserContext.require()));
    }

    /**
     * 新增插件。
     *
     * @param dto 入参
     * @return 回显
     */
    @PostMapping("/mine")
    public Result<AiToolDetailVO> create(@Valid @RequestBody AiToolSaveDTO dto) {
        dto.setId(null);
        return Result.ok(aiToolService.save(UserContext.require(), dto));
    }

    /**
     * 更新插件。
     *
     * @param id  主键
     * @param dto 入参
     * @return 回显
     */
    @PutMapping("/mine/{id}")
    public Result<AiToolDetailVO> update(@PathVariable Long id, @Valid @RequestBody AiToolSaveDTO dto) {
        dto.setId(id);
        return Result.ok(aiToolService.save(UserContext.require(), dto));
    }

    /**
     * 删除插件。
     *
     * @param id 主键
     * @return 空
     */
    @DeleteMapping("/mine/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        aiToolService.delete(UserContext.require(), id);
        return Result.ok();
    }

    /**
     * 启用 / 停用。
     *
     * @param id 主键
     * @return 回显
     */
    @PutMapping("/mine/{id}/toggle")
    public Result<AiToolDetailVO> toggle(@PathVariable Long id) {
        return Result.ok(aiToolService.toggle(UserContext.require(), id));
    }
}
