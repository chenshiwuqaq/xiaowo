package org.example.mynote.service;

import org.example.mynote.dto.TagSaveDTO;
import org.example.mynote.dto.TagUpdateDTO;
import org.example.mynote.vo.TagVO;

import java.util.List;

/**
 * 标签业务服务。
 *
 * <p>标签是用户级全局池（同一用户下名称唯一），多篇笔记共用；删除标签只解绑关联，
 * 不动笔记本体。</p>
 *
 * @author MyNote
 */
public interface TagService {

    /**
     * 当前用户的全部标签，附引用笔记数。
     *
     * @return 标签列表
     */
    List<TagVO> list();

    /**
     * 新建标签：同名拒绝，颜色缺省补默认色。
     *
     * @param dto 新建入参
     * @return 新标签
     */
    TagVO create(TagSaveDTO dto);

    /**
     * 修改标签名 / 颜色，改名需保持同用户下唯一。
     *
     * @param id  标签 id
     * @param dto 编辑入参
     * @return 更新后的标签
     */
    TagVO update(Long id, TagUpdateDTO dto);

    /**
     * 删除标签并解除其与笔记的全部关联。
     *
     * @param id 标签 id
     */
    void delete(Long id);
}
