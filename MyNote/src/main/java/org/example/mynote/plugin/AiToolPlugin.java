package org.example.mynote.plugin;

/**
 * AI 工具插件契约（SPI）。
 *
 * <p>这是整个「可插拔」架构的核心：任何想成为 AI 工具的能力，只要实现这个接口并注册到
 * {@link AiToolRegistry}，就会自动出现在助手面板与通用执行接口里。内置的「背景知识」「文献推荐」
 * 只是这个契约的两个实现；用户自己写的声明式插件则由 {@link DeclarativeToolPlugin} 动态承载。</p>
 *
 * <p>契约刻意只依赖 {@link AiToolContext}（能力注入）与 {@link AiToolRequest}/{@link AiToolResult}
 * （通用入出参），不依赖 Spring 装配。这样后续接入脚本插件（GraalVM 等）时，脚本里看到的也是
 * 同一套稳定接口，不用返工。</p>
 *
 * @author MyNote
 */
public interface AiToolPlugin {

    /**
     * 稳定唯一 id。内置插件用短名（如 {@code knowledge}）；用户插件在注册表里统一加
     * {@code user:} 前缀，避免与内置插件撞名。
     *
     * @return 插件 id
     */
    String id();

    /**
     * 展示名。
     *
     * @return 名称
     */
    String name();

    /**
     * 一句话说明，前端悬停或列表里展示。
     *
     * @return 说明
     */
    String description();

    /**
     * 是否依赖大模型。离线也能出结果的工具（如本地知识卡片命中）返回 false。
     *
     * @return true 表示必须调用模型
     */
    boolean requiresModel();

    /**
     * 渲染类型：{@code text}（富文本，可插入正文）/ {@code cards}（卡片列表）/ {@code references}（文献列表）。
     *
     * @return 渲染类型
     */
    String renderType();

    /**
     * 文本结果是否允许一键插入正文。
     *
     * @return true 表示可插入
     */
    boolean insertable();

    /**
     * 执行插件。
     *
     * @param ctx     能力上下文，含当前用户与共享服务
     * @param request 入参（笔记、选区、会话与自定义参数）
     * @return 结果
     */
    AiToolResult run(AiToolContext ctx, AiToolRequest request);
}
