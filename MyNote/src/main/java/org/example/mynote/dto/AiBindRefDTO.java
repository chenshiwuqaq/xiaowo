package org.example.mynote.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 把文献绑定到笔记。
 *
 * @author MyNote
 */
@Data
public class AiBindRefDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 笔记 id
     */
    @NotNull(message = "缺少笔记 id")
    private Long noteId;

    /**
     * 文献 id，为 0 或空表示解除绑定
     */
    @NotNull(message = "缺少文献 id")
    private Long refId;

    /**
     * 来源：ai / manual
     */
    private String source;

}
