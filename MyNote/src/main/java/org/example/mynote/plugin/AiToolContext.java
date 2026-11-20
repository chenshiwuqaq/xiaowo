package org.example.mynote.plugin;

import org.example.mynote.ai.AiRuntimeConfig;
import org.example.mynote.ai.NoteContextBuilder;
import org.example.mynote.ai.OpenAiClient;
import org.example.mynote.config.AppProperties;
import org.example.mynote.dao.NoteDAO;
import org.example.mynote.service.AiConfigService;

/**
 * 插件能力上下文（对应 DeepSeek Harness 里插件拿到的 {@code ctx}）。
 *
 * <p>每个请求由注册表新建一份，注入当前用户与共享服务。插件只依赖这个对象，
 * 不直接接触 Spring 容器，从而把「插件能做什么」收窄到一组显式能力上——这也是
 * 后续脚本插件做安全沙箱时的边界。</p>
 *
 * @author MyNote
 */
public class AiToolContext {

    private final Long userId;

    private final NoteDAO noteDAO;

    private final OpenAiClient openAiClient;

    private final AiConfigService aiConfigService;

    private final NoteContextBuilder noteContextBuilder;

    private final AppProperties appProperties;

    public AiToolContext(Long userId, NoteDAO noteDAO, OpenAiClient openAiClient, AiConfigService aiConfigService,
                         NoteContextBuilder noteContextBuilder, AppProperties appProperties) {
        this.userId = userId;
        this.noteDAO = noteDAO;
        this.openAiClient = openAiClient;
        this.aiConfigService = aiConfigService;
        this.noteContextBuilder = noteContextBuilder;
        this.appProperties = appProperties;
    }

    public Long userId() {
        return userId;
    }

    public NoteDAO noteDAO() {
        return noteDAO;
    }

    public OpenAiClient openAiClient() {
        return openAiClient;
    }

    public AiConfigService aiConfigService() {
        return aiConfigService;
    }

    public NoteContextBuilder noteContextBuilder() {
        return noteContextBuilder;
    }

    public AppProperties appProperties() {
        return appProperties;
    }

    /**
     * 取当前用户默认模型的运行时配置，未配置时返回 null。
     *
     * @return 运行时配置
     */
    public AiRuntimeConfig currentRuntime() {
        return aiConfigService.currentRuntime(userId);
    }

    /**
     * 组装笔记上下文。
     *
     * @param noteId 笔记 id，可为空
     * @return 上下文
     */
    public NoteContextBuilder.Context buildContext(Long noteId) {
        return noteContextBuilder.build(userId, noteId);
    }
}
