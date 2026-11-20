package org.example.mynote.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 新建标签入参。
 *
 * <p>颜色可空，服务端补默认色，保证标签展示层永远拿得到色值。</p>
 *
 * @author MyNote
 */
@Data
public class TagSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 标签名，同一用户下唯一
     */
    @NotBlank(message = "标签名不能为空")
    @Size(max = 50, message = "标签名不能超过 50 字")
    private String name;

    /**
     * 颜色值，如 #E7A878；不传使用默认色
     */
    @Size(max = 20, message = "颜色值格式不正确")
    private String color;
}
