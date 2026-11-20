package org.example.mynote.plugin.builtin;

import org.example.mynote.plugin.AiToolContext;
import org.example.mynote.plugin.AiToolPlugin;
import org.example.mynote.plugin.AiToolRequest;
import org.example.mynote.plugin.AiToolResult;
import org.example.mynote.service.ScholarService;
import org.springframework.stereotype.Component;

/**
 * 内置插件：参考文献推荐。
 *
 * <p>与背景知识一样是薄适配，把 {@link ScholarService} 挂到插件契约上，
 * 旧接口 {@code /api/ai/references} 保持不变。</p>
 *
 * @author MyNote
 */
@Component
public class ReferenceToolPlugin implements AiToolPlugin {

    private final ScholarService scholarService;

    public ReferenceToolPlugin(ScholarService scholarService) {
        this.scholarService = scholarService;
    }

    @Override
    public String id() {
        return "references";
    }

    @Override
    public String name() {
        return "参考文献";
    }

    @Override
    public String description() {
        return "检索与当前笔记相关的文献，合并本机文献库与联网最新结果，可绑定到笔记归档。";
    }

    @Override
    public boolean requiresModel() {
        return false;
    }

    @Override
    public String renderType() {
        return "references";
    }

    @Override
    public boolean insertable() {
        return false;
    }

    @Override
    public AiToolResult run(AiToolContext ctx, AiToolRequest request) {
        return AiToolResult.references(scholarService.recommend(ctx.userId(), request.getNoteId()));
    }
}
