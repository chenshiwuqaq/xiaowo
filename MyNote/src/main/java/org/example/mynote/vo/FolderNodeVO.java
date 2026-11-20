package org.example.mynote.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 目录树节点视图对象，children 递归嵌套。
 *
 * <p>noteCount 只统计直接挂在本目录下的未删除笔记（不含子目录），
 * 与侧栏"目录旁的小数字"口径一致。</p>
 *
 * @author MyNote
 */
@Data
public class FolderNodeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 目录 id
     */
    private Long id;

    /**
     * 父目录 id，0 为根
     */
    private Long parentId;

    /**
     * 目录名
     */
    private String name;

    /**
     * 图标标识
     */
    private String icon;

    /**
     * 同级排序号
     */
    private Integer sortOrder;

    /**
     * 本目录下未删除笔记数
     */
    private Integer noteCount;

    /**
     * 子目录节点
     */
    private List<FolderNodeVO> children = new ArrayList<>();
}
