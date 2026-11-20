package org.example.mynote.dao;

import org.example.mynote.entity.MarketCommentDO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 插件市场评论 DAO。
 *
 * @author MyNote
 */
@Repository
public interface MarketCommentDAO extends JpaRepository<MarketCommentDO, Long> {

    /**
     * 某条目的评论，按时间倒序（新评论在前）。
     */
    List<MarketCommentDO> findByPluginIdOrderByCreateTimeDesc(Long pluginId);

    /**
     * 删除条目时清理其全部评论。
     */
    @Modifying
    @Query("delete from MarketCommentDO c where c.pluginId = :pluginId")
    void deleteByPluginId(@Param("pluginId") Long pluginId);
}
