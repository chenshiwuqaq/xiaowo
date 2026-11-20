package org.example.mynote.dao;

import org.example.mynote.entity.KnowledgeCardDO;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 创作背景知识库 DAO。

 *
 * @author MyNote
 */
@Repository
public interface KnowledgeCardDAO extends JpaRepository<KnowledgeCardDO, Long> {

    /**
     * 查询全部知识卡片。语料量为数百条级，命中匹配在内存中完成比 SQL 里做关键词 LIKE 更可控。
     *
     * @return 卡片列表
     */
    List<KnowledgeCardDO> findAllByOrderByIdAsc();

    /**
     * 按分类查询。
     *
     * @param category 分类
     * @return 卡片列表
     */
    List<KnowledgeCardDO> findByCategory(String category);

    /**
     * 采纳一次，计数加一，可作为后续排序权重。
     *
     * @param id 卡片 id
     */
    @Modifying
    @Query("update KnowledgeCardDO k set k.refCount = k.refCount + 1 where k.id = :id")
    void increaseRefCount(@Param("id") Long id);
}
