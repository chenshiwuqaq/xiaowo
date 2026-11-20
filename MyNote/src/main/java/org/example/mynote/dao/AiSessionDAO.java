package org.example.mynote.dao;

import org.example.mynote.entity.AiSessionDO;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * AI 会话 DAO。

 *
 * @author MyNote
 */
@Repository
public interface AiSessionDAO extends JpaRepository<AiSessionDO, Long> {

    /**
     * 用户会话列表，按最近活跃倒序。
     *
     * @param userId 用户 id
     * @param limit  分页参数，限制返回条数
     * @return 会话列表
     */
    @Query("select s from AiSessionDO s where s.userId = :userId order by s.updateTime desc")
    List<AiSessionDO> listByUser(@Param("userId") Long userId, Pageable limit);

    /**
     * 某篇笔记下的会话。
     *
     * @param userId 用户 id
     * @param noteId 笔记 id
     * @return 会话列表
     */
    List<AiSessionDO> findByUserIdAndNoteIdOrderByIdDesc(Long userId, Long noteId);

    /**
     * 按 id 与用户查询。
     *
     * @param id     会话 id
     * @param userId 用户 id
     * @return 会话
     */
    AiSessionDO findFirstByIdAndUserId(Long id, Long userId);
}
