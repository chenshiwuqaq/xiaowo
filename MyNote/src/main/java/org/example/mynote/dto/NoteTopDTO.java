package org.example.mynote.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 笔记置顶开关入参。
 *
 * @author MyNote
 */
@Data
public class NoteTopDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 笔记 id
     */
    @NotNull(message = "笔记 id 不能为空")
    private Long id;

    /**
     * true 置顶，false 取消置顶
     */
    @NotNull(message = "置顶状态不能为空")
    private Boolean top;
}
