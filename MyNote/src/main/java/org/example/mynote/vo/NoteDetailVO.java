package org.example.mynote.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 笔记详情视图对象 = 列表项 + 正文，编辑器打开单篇笔记时使用。
 *
 * @author MyNote
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class NoteDetailVO extends NoteVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Markdown 正文
     */
    private String content;
}
