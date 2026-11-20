package org.example.mynote.dao;

import org.example.mynote.entity.MarketPluginDO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 插件市场条目 DAO。
 *
 * <p>检索统一走 LIKE（个人自托管市场数据量有限，够用）。关键词命中名称、插件标识、
 * 作者昵称、说明与标签；分类用 -1 表示「全部」，与 {@code Constants.NOTE_TYPE_*} 对齐。</p>
 *
 * @author MyNote
 */
@Repository
public interface MarketPluginDAO extends JpaRepository<MarketPluginDO, Long> {

    /**
     * 上架条目的检索，排序由 Pageable 传入（createTime / likeCount / commentCount / useCount）。
     */
    @Query("select p from MarketPluginDO p where p.status = 1 "
            + "and (:keyword = '' or p.name like concat('%', :keyword, '%') "
            + "or p.toolId like concat('%', :keyword, '%') "
            + "or p.authorName like concat('%', :keyword, '%') "
            + "or p.description like concat('%', :keyword, '%') "
            + "or p.tags like concat('%', :keyword, '%')) "
            + "and (:category = -1 or p.category = :category)")
    Page<MarketPluginDO> searchPublished(@Param("keyword") String keyword,
                                         @Param("category") Integer category,
                                         Pageable pageable);

    /**
     * 上架条目的 id 列表（换一批时先取 id、随机打乱、再按需加载）。
     */
    @Query("select p.id from MarketPluginDO p where p.status = 1 "
            + "and (:keyword = '' or p.name like concat('%', :keyword, '%') "
            + "or p.toolId like concat('%', :keyword, '%') "
            + "or p.authorName like concat('%', :keyword, '%') "
            + "or p.description like concat('%', :keyword, '%') "
            + "or p.tags like concat('%', :keyword, '%')) "
            + "and (:category = -1 or p.category = :category)")
    List<Long> findPublishedIds(@Param("keyword") String keyword,
                                @Param("category") Integer category);

    /**
     * 某用户的全部发布（含下架），供「我的发布」管理页使用。
     */
    List<MarketPluginDO> findByAuthorIdOrderByCreateTimeDesc(Long authorId);

    /**
     * 按主键与作者查询（作者本人编辑/删除用）。
     */
    MarketPluginDO findFirstByIdAndAuthorId(Long id, Long authorId);
}
