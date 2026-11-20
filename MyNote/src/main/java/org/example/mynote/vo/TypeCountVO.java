package org.example.mynote.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 某一类笔记的数量，主页"作品构成"环形图的一个扇区。
 *
 * <p>noteType 保留原始数字编码供前端做配色映射，name 由服务端翻译成中文，
 * 这样文案只有一处定义，前端不必再抄一份类型码表，两者也不会各说各话。</p>
 *
 * @author MyNote
 */
@Data
public class TypeCountVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 笔记类型编码：1 科研，2 生活，3 小说，4 随笔
     */
    private Integer noteType;

    /**
     * 类型中文名
     */
    private String name;

    /**
     * 该类型下未删除笔记的篇数
     */
    private Long count;
}
