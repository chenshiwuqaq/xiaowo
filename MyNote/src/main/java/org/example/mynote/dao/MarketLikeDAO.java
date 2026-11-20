package org.example.mynote.dao;

import org.example.mynote.entity.MarketLikeDO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 插件市场点赞明细 DAO。
 *
 * @author MyNote
 */
@Repository
public interface MarketLikeDAO extends JpaRepository<MarketLikeDO, Long> {

    /**
     * 查询某用户是否已点赞某条目。
     */
    MarketLikeDO findFirstByPluginIdAndUserId(Long pluginId, Long userId);

    /**
     * 批量返回用户已点赞的条目 id（用于卡片列表回显爱心状态）。
     */
    @Query("select l.pluginId from MarketLikeDO l where l.userId = :userId and l.pluginId in :pluginIds")
    List<Long> findLikedPluginIds(@Param("userId") Long userId, @Param("pluginIds") List<Long> pluginIds);

    /**
     * 取消点赞。
     */
    @Modifying
    @Query("delete from MarketLikeDO l where l.pluginId = :pluginId and l.userId = :userId")
    void deleteByPluginIdAndUserId(@Param("pluginId") Long pluginId, @Param("userId") Long userId);

    /**
     * 删除条目时清理其全部点赞明细。
     */
    @Modifying
    @Query("delete from MarketLikeDO l where l.pluginId = :pluginId")
    void deleteByPluginId(@Param("pluginId") Long pluginId);
}
