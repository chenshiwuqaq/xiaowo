package org.example.mynote.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 目录移动入参。
 *
 * <p>只换父级、不改内容，因此不需要 name/icon；parentId = 0 表示移动到根目录。</p>
 *
 * @author MyNote
 */
@Data
public class FolderMoveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 待移动目录 id
     */
    @NotNull(message = "目录 id 不能为空")
    private Long id;

    /**
     * 目标父目录 id，0 表示根目录
     */
    @NotNull(message = "目标父目录不能为空")
    @Min(value = 0, message = "父目录 id 不能为负数")
    private Long parentId;
}
