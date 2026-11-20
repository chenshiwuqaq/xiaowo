package org.example.mynote.plugin.builtin;

import org.example.mynote.plugin.AiToolContext;
import org.example.mynote.plugin.AiToolPlugin;
import org.example.mynote.plugin.AiToolRequest;
import org.example.mynote.plugin.AiToolResult;
import org.example.mynote.service.KnowledgeService;
import org.springframework.stereotype.Component;

/**
 * 内置插件：背景知识浮现。
 *
 * <p>这是一层薄适配：把既有 {@link KnowledgeService} 的能力挂到插件契约上，
 * 业务逻辑保持不变，旧接口 {@code /api/ai/knowledge} 仍直接调用该服务。</p>
 *
 * @author MyNote
 */
@Component
public class KnowledgeToolPlugin implements AiToolPlugin {

    private final KnowledgeService knowledgeService;

    public KnowledgeToolPlugin(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @Override
    public String id() {
        return "knowledge";
    }

    @Override
    public String name() {
        return "背景知识";
    }

    @Override
    public String description() {
        return "扫描正文，浮现与时代、地域、生活相关的常识卡片，可直接采纳进正文。";
    }

    @Override
    public boolean requiresModel() {
        return false;
    }

    @Override
    public String renderType() {
        return "cards";
    }

    @Override
    public boolean insertable() {
        return false;
    }

    @Override
    public AiToolResult run(AiToolContext ctx, AiToolRequest request) {
        return AiToolResult.cards(
                knowledgeService.surface(ctx.userId(), request.getNoteId(), request.getSelection()));
    }
}
