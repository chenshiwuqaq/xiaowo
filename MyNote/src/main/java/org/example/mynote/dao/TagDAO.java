package org.example.mynote.dao;

import org.example.mynote.entity.TagDO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 标签 DAO。标签是全局标签池，同一用户下名称唯一。

 *
 * @author MyNote
 */
@Repository
public interface TagDAO extends JpaRepository<TagDO, Long> {

    /**
     * 用户的全部标签。
     *
     * @param userId 用户 id
     * @return 标签列表
     */
    List<TagDO> findByUserIdOrderByIdAsc(Long userId);

    /**
     * 按名称查标签，用于"输入即复用"已有标签。
     *
     * @param userId 用户 id
     * @param name   标签名
     * @return 标签
     */
    TagDO findFirstByUserIdAndName(Long userId, String name);

    /**
     * 按 id 与用户共同限定，阻止越权操作他人标签。
     *
     * @param id     标签 id
     * @param userId 用户 id
     * @return 标签
     */
    TagDO findFirstByIdAndUserId(Long id, Long userId);
}
