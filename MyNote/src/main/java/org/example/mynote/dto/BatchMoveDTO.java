package org.example.mynote.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 批量移动笔记入参。
 *
 * <p>folderId = 0 表示批量移出到"未分类"，因此只校验非负、不校验非零。</p>
 *
 * @author MyNote
 */
@Data
public class BatchMoveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 笔记 id 集合，任一 id 不属于当前用户则整单失败
     */
    @NotEmpty(message = "请选择要移动的笔记")
    private List<Long> ids;

    /**
     * 目标目录 id，0 表示未分类
     */
    @NotNull(message = "目标目录不能为空")
    @Min(value = 0, message = "目录 id 不能为负数")
    private Long folderId;
}
