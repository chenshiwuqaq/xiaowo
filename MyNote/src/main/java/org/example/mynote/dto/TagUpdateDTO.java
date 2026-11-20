package org.example.mynote.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 编辑标签入参。
 *
 * <p>标签只改自身属性（名称与颜色），不影响已绑定的笔记，故与新建分开定义，
 * 后续若增加"重命名同步关联"等差异行为时不必拆公共 DTO。</p>
 *
 * @author MyNote
 */
@Data
public class TagUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 新标签名，同一用户下仍需唯一
     */
    @NotBlank(message = "标签名不能为空")
    @Size(max = 50, message = "标签名不能超过 50 字")
    private String name;

    /**
     * 新颜色值；不传保持原色
     */
    @Size(max = 20, message = "颜色值格式不正确")
    private String color;
}
