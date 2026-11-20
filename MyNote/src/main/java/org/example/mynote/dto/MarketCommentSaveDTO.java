package org.example.mynote.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 插件市场评论入参。
 *
 * @author MyNote
 */
@Data
public class MarketCommentSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 1000, message = "评论太长（1000 字以内）")
    private String content;
}
