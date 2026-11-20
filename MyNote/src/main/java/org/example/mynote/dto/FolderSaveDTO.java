package org.example.mynote.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 目录新建与编辑入参。
 *
 * <p>新建与更新共用一个 DTO：新建时 parentId 决定挂载位置（null 视为根目录），
 * 更新时 parentId 无意义（目录换父级走独立的 move 接口），只有 name/icon/sortOrder 生效。</p>
 *
 * @author MyNote
 */
@Data
public class FolderSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 父目录 id，0 或不传表示建在根下
     */
    @Min(value = 0, message = "父目录 id 不能为负数")
    private Long parentId;

    /**
     * 目录名，同级下不允许重名
     */
    @NotBlank(message = "目录名不能为空")
    @Size(max = 100, message = "目录名不能超过 100 字")
    private String name;

    /**
     * 图标标识，由前端图标库选择
     */
    @Size(max = 50, message = "图标标识不能超过 50 字")
    private String icon;

    /**
     * 同级排序号，越小越靠前
     */
    private Integer sortOrder;
}
