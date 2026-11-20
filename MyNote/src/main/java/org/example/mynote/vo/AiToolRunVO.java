package org.example.mynote.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 通用执行结果：渲染类型 + 数据体，前端按渲染类型通用渲染。
 *
 * @author MyNote
 */
@Data
public class AiToolRunVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 生效的插件 id
     */
    private String toolId;

    /**
     * 渲染类型：text / cards / references
     */
    private String renderType;

    /**
     * 数据体，结构随渲染类型约定
     */
    private Object data;
}
