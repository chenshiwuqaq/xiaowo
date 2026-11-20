package org.example.mynote.dao;

import org.example.mynote.entity.ReferenceDO;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 参考文献 DAO。本地文献库为离线兜底，联网可用时由 ScholarService 合并最新结果。

 *
 * @author MyNote
 */
@Repository
public interface ReferenceDAO extends JpaRepository<ReferenceDO, Long> {

    /**
     * 按关键词在标题、关键词、学科方向、摘要中匹配文献。
     *
     * @param pattern 已拼好通配符的匹配串
     * @param limit   分页参数，用于限制返回条数
     * @return 文献列表
     */
    @Query("select r from ReferenceDO r where r.title like :pattern or r.keywords like :pattern "
            + "or r.field like :pattern or r.abstractText like :pattern "
            + "order by r.pubYear desc, r.citedCount desc")
    List<ReferenceDO> matchByKeyword(@Param("pattern") String pattern, Pageable limit);

    /**
     * 按 id 批量查询。
     *
     * @param ids id 集合
     * @return 文献列表
     */
    List<ReferenceDO> findByIdIn(List<Long> ids);
}
