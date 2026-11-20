package org.example.mynote.dao;

import org.example.mynote.entity.DiagramDO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 逻辑图 DAO，存 Mermaid 源码。

 *
 * @author MyNote
 */
@Repository
public interface DiagramDAO extends JpaRepository<DiagramDO, Long> {

    /**
     * 用户的逻辑图列表。
     *
     * @param userId 用户 id
     * @return 图列表
     */
    List<DiagramDO> findByUserIdOrderByCreateTimeDesc(Long userId);

    /**
     * 按 id 与用户查询。
     *
     * @param id     图 id
     * @param userId 用户 id
     * @return 图
     */
    DiagramDO findFirstByIdAndUserId(Long id, Long userId);
}
