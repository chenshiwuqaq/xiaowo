package org.example.mynote.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户插件的管理回显（含可编辑字段，供「我的插件」页使用）。
 *
 * @author MyNote
 */
@Data
public class AiToolDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String toolId;

    private String name;

    private String description;

    private String renderType;

    private String systemPrompt;

    private String userTemplate;

    private Boolean insertable;

    private Boolean enabled;

    private Integer sortOrder;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
