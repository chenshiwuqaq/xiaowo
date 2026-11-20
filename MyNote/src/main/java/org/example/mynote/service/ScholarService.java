package org.example.mynote.service;

import org.example.mynote.vo.ReferenceResultVO;

/**
 * 参考文献推荐服务（科研分支）。
 *
 * @author MyNote
 */
public interface ScholarService {

    /**
     * 检索与当页笔记内容相关的文献，按相关度排序。
     *
     * @param userId 用户 id
     * @param noteId 笔记 id
     * @return 推荐结果，含检索词与来源标记
     */
    ReferenceResultVO recommend(Long userId, Long noteId);

    /**
     * 把文献绑定到笔记，作为"已采纳"归档。
     *
     * @param userId 用户 id
     * @param noteId 笔记 id
     * @param refId  文献 id
     * @param source ai / manual
     */
    void bind(Long userId, Long noteId, Long refId, String source);

    /**
     * 解除绑定。
     *
     * @param userId 用户 id
     * @param noteId 笔记 id
     * @param refId  文献 id
     */
    void unbind(Long userId, Long noteId, Long refId);
}
