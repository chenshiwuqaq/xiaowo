package org.example.mynote.plugin;

import org.example.mynote.ai.AiRuntimeConfig;
import org.example.mynote.ai.NoteContextBuilder;
import org.example.mynote.ai.OpenAiClient;
import org.example.mynote.common.BizException;
import org.example.mynote.common.ResultCode;
import org.example.mynote.entity.AiToolDO;

import java.util.List;

/**
 * 用户声明式插件的运行时载体。
 *
 * <p>每个启用的用户插件对应一个实例：从 {@link AiToolDO} 读 manifest，
 * 执行时把笔记上下文 / 选区 / 邻域代入模板变量，拼成 system + user 两条消息调用大模型，
 * 返回可插入正文的文本结果。变量支持：{@code {{note}}}、{@code {{selection}}}、
 * {@code {{siblings}}}、{@code {{title}}}。</p>
 *
 * @author MyNote
 */
public class DeclarativeToolPlugin implements AiToolPlugin {

    private static final String DEFAULT_TEMPLATE = "{{note}}{{selection}}";

    private final AiToolDO tool;

    public DeclarativeToolPlugin(AiToolDO tool) {
        this.tool = tool;
    }

    @Override
    public String id() {
        return AiToolRegistry.USER_PREFIX + tool.getToolId();
    }

    @Override
    public String name() {
        return tool.getName();
    }

    @Override
    public String description() {
        return tool.getDescription() == null ? "" : tool.getDescription();
    }

    @Override
    public boolean requiresModel() {
        return true;
    }

    @Override
    public String renderType() {
        return tool.getRenderType() == null ? "text" : tool.getRenderType();
    }

    @Override
    public boolean insertable() {
        return Boolean.TRUE.equals(tool.getInsertable());
    }

    @Override
    public AiToolResult run(AiToolContext ctx, AiToolRequest request) {
        AiRuntimeConfig runtime = ctx.currentRuntime();
        if (runtime == null) {
            throw new BizException(ResultCode.AI_NOT_CONFIGURED);
        }
        NoteContextBuilder.Context context = ctx.buildContext(request.getNoteId());
        String system = tool.getSystemPrompt() == null ? "" : tool.getSystemPrompt();
        String template = blank(tool.getUserTemplate()) ? DEFAULT_TEMPLATE : tool.getUserTemplate();
        String user = render(template, context, request.getSelection());
        if (user.isBlank()) {
            user = "请根据你的设定，对当前内容给出输出。";
        }
        List<OpenAiClient.Message> messages = List.of(
                new OpenAiClient.Message("system", system),
                new OpenAiClient.Message("user", user));
        String answer = ctx.openAiClient().chatOnce(runtime, messages);
        return AiToolResult.text(answer, runtime.getModelName(), insertable());
    }

    /**
     * 模板变量替换。
     *
     * @param template  模板
     * @param context   笔记上下文
     * @param selection 选区
     * @return 渲染结果
     */
    private String render(String template, NoteContextBuilder.Context context, String selection) {
        String title = context.getNote() == null ? "" : nvl(context.getNote().getTitle());
        return template
                .replace("{{note}}", nvl(context.getNoteBlock()))
                .replace("{{selection}}", nvl(selection))
                .replace("{{siblings}}", nvl(context.getSiblingBlock()))
                .replace("{{title}}", title);
    }

    private static String nvl(String text) {
        return text == null ? "" : text;
    }

    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }
}
