package org.example.mynote.service;

import org.example.mynote.dto.FolderMoveDTO;
import org.example.mynote.dto.FolderSaveDTO;
import org.example.mynote.vo.FolderNodeVO;

import java.util.List;

/**
 * 目录业务服务。
 *
 * <p>目录是用户私有树（parent_id 自关联，0 为根），删除一律逻辑删除且绝不连带删笔记——
 * 笔记落回"未分类"比跟着目录蒸发安全得多。</p>
 *
 * @author MyNote
 */
public interface FolderService {

    /**
     * 组装当前用户的完整目录树，节点带本目录直接笔记数。
     *
     * @return 根目录节点列表
     */
    List<FolderNodeVO> tree();

    /**
     * 新建目录：校验父目录归属与同级重名。
     *
     * @param dto 新建入参
     * @return 新目录节点
     */
    FolderNodeVO create(FolderSaveDTO dto);

    /**
     * 重命名 / 改图标 / 调排序。
     *
     * @param id  目录 id
     * @param dto 编辑入参
     * @return 更新后的目录节点
     */
    FolderNodeVO update(Long id, FolderSaveDTO dto);

    /**
     * 移动目录：禁止移入自身或自身子孙，防止树出现环。
     *
     * @param dto 移动入参
     */
    void move(FolderMoveDTO dto);

    /**
     * 删除目录：连同子孙目录逻辑删除，目录下笔记落回未分类。
     *
     * @param id 目录 id
     */
    void delete(Long id);
}
