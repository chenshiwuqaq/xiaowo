package org.example.mynote.dao;

import org.example.mynote.entity.MarketUseDO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 插件市场使用/下载明细 DAO。
 *
 * @author MyNote
 */
@Repository
public interface MarketUseDAO extends JpaRepository<MarketUseDO, Long> {

    /**
     * 查询某用户是否已使用过某条目（去重）。
     */
    MarketUseDO findFirstByPluginIdAndUserId(Long pluginId, Long userId);

    /**
     * 批量返回用户已使用过的条目 id（用于卡片列表回显「已安装」状态）。
     */
    @Query("select u.pluginId from MarketUseDO u where u.userId = :userId and u.pluginId in :pluginIds")
    List<Long> findUsedPluginIds(@Param("userId") Long userId, @Param("pluginIds") List<Long> pluginIds);

    /**
     * 删除条目时清理其全部使用明细。
     */
    @Modifying
    @Query("delete from MarketUseDO u where u.pluginId = :pluginId")
    void deleteByPluginId(@Param("pluginId") Long pluginId);
}
