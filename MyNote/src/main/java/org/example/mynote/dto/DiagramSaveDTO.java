package org.example.mynote.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 保存生成好的逻辑图。
 *
 * @author MyNote
 */
@Data
public class DiagramSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 标题
     */
    @NotBlank(message = "给这张图起个名字")
    private String title;

    /**
     * 图类型
     */
    private String diagramType;

    /**
     * 参与笔记 id，逗号分隔
     */
    private String noteIds;

    /**
     * Mermaid 源码
     */
    @NotBlank(message = "图为空，无法保存")
    private String code;

}
