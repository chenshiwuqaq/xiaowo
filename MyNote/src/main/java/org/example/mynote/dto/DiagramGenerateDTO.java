package org.example.mynote.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serializable;

/**
 * 多篇笔记生成逻辑图。
 *
 * @author MyNote
 */
@Data
public class DiagramGenerateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 参与生成的笔记 id
     */
    @NotEmpty(message = "先在列表里勾选至少两篇笔记")
    private java.util.List<Long> noteIds;

    /**
     * 图类型：mindmap/flowchart/graph/timeline
     */
    private String type;

    /**
     * 是否调用模型重排结构；false 时走本地算法，离线可用
     */
    private Boolean useAi;

}
