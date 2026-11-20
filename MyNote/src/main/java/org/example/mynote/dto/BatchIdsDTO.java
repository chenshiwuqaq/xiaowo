package org.example.mynote.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 批量笔记操作入参：批量进回收站只用 ids；批量导出额外用 format。
 *
 * <p>两个场景共用本 DTO，因为结构只差一个可选项，拆开会得到两个几乎相同的类。</p>
 *
 * @author MyNote
 */
@Data
public class BatchIdsDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 笔记 id 集合，任一 id 不属于当前用户则整单失败
     */
    @NotEmpty(message = "请选择要操作的笔记")
    private List<Long> ids;

    /**
     * 导出格式：md 原样导出 Markdown 源文，txt 去语法导出纯文本；仅批量导出接口使用
     */
    @Pattern(regexp = "md|txt", message = "导出格式只支持 md 或 txt")
    private String format;
}
