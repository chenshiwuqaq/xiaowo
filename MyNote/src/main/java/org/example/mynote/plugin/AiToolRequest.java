package org.example.mynote.plugin;

import java.util.Collections;
import java.util.Map;

/**
 * 插件执行的通用入参。
 *
 * <p>与 HTTP 层的 {@code AiToolRunDTO} 一一对应，但这里是插件层的稳定类型：
 * 脚本插件未来拿到的也是它，不耦合 DTO 校验注解。</p>
 *
 * @author MyNote
 */
public class AiToolRequest {

    private final Long noteId;

    private final String selection;

    private final Long sessionId;

    private final Map<String, Object> params;

    public AiToolRequest(Long noteId, String selection, Long sessionId, Map<String, Object> params) {
        this.noteId = noteId;
        this.selection = selection;
        this.sessionId = sessionId;
        this.params = params == null ? Collections.emptyMap() : params;
    }

    public Long getNoteId() {
        return noteId;
    }

    public String getSelection() {
        return selection;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public Map<String, Object> getParams() {
        return params;
    }
}
