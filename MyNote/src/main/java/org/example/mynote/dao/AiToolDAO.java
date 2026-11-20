package org.example.mynote.dao;

import org.example.mynote.entity.AiToolDO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 用户自定义 AI 工具插件 DAO。
 *
 * @author MyNote
 */
@Repository
public interface AiToolDAO extends JpaRepository<AiToolDO, Long> {

    /**
     * 用户的全部插件，按排序号与 id 升序。
     *
     * @param userId 用户 id
     * @return 插件列表
     */
    List<AiToolDO> findByUserIdOrderBySortOrderAscIdAsc(Long userId);

    /**
     * 按主键与用户查询。
     *
     * @param id     主键
     * @param userId 用户 id
     * @return 插件
     */
    AiToolDO findFirstByIdAndUserId(Long id, Long userId);

    /**
     * 按插件标识与用户查询（账号内 tool_id 唯一）。
     *
     * @param userId 用户 id
     * @param toolId 插件标识
     * @return 插件
     */
    AiToolDO findFirstByUserIdAndToolId(Long userId, String toolId);

    /**
     * 删除插件。
     *
     * @param id     主键
     * @param userId 用户 id
     */
    @Modifying
    @Query("delete from AiToolDO t where t.id = :id and t.userId = :userId")
    void deleteByIdAndUser(@Param("id") Long id, @Param("userId") Long userId);
}
