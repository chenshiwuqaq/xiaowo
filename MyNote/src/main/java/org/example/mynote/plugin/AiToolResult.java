package org.example.mynote.plugin;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 插件执行的通用出参：渲染类型 + 数据体。
 *
 * <p>数据体按渲染类型各约定一份结构，前端据此通用渲染，不再为每个工具写专用组件：</p>
 * <ul>
 *   <li>{@code text}：{@code {text, model, insertable}}</li>
 *   <li>{@code cards}：直接透传 {@code KnowledgeResultVO}</li>
 *   <li>{@code references}：直接透传 {@code ReferenceResultVO}</li>
 * </ul>
 *
 * @author MyNote
 */
public class AiToolResult {

    private final String renderType;

    private final Object data;

    private AiToolResult(String renderType, Object data) {
        this.renderType = renderType;
        this.data = data;
    }

    /**
     * 文本结果。
     *
     * @param text       正文
     * @param model      模型名
     * @param insertable 是否可插入正文
     * @return 结果
     */
    public static AiToolResult text(String text, String model, boolean insertable) {
        Map<String, Object> payload = new LinkedHashMap<>(3);
        payload.put("text", text == null ? "" : text);
        payload.put("model", model == null ? "" : model);
        payload.put("insertable", insertable);
        return new AiToolResult("text", payload);
    }

    /**
     * 卡片列表结果（背景知识等）。
     *
     * @param data 结果 VO
     * @return 结果
     */
    public static AiToolResult cards(Object data) {
        return new AiToolResult("cards", data);
    }

    /**
     * 文献列表结果。
     *
     * @param data 结果 VO
     * @return 结果
     */
    public static AiToolResult references(Object data) {
        return new AiToolResult("references", data);
    }

    public String getRenderType() {
        return renderType;
    }

    public Object getData() {
        return data;
    }
}
