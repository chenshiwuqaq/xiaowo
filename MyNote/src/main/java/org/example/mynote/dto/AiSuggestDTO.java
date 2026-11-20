package org.example.mynote.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 飘窗建议入参。
 *
 * @author MyNote
 */
@Data
public class AiSuggestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 笔记 id
     */
    @NotNull(message = "缺少笔记 id")
    private Long noteId;

    /**
     * 光标附近或选中的文字，可空
     */
    private String selection;

    /**
     * 上一轮已经给出的建议，重生成时用于避免重复
     */
    private List<String> previous;

}
