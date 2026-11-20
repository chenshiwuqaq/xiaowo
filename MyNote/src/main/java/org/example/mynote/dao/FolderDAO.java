package org.example.mynote.dao;

import org.example.mynote.entity.FolderDO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 目录 DAO。
 *
 * @author MyNote
 */
@Repository
public interface FolderDAO extends JpaRepository<FolderDO, Long> {

    /**
     * 查询用户全部未删除目录，按排序号升序，供前端组装树。
     *
     * @param userId 用户 id
     * @return 目录列表
     */
    List<FolderDO> findByUserIdAndDeletedFalseOrderBySortOrderAsc(Long userId);

    /**
     * 查询某父目录下的子目录。
     *
     * @param userId   用户 id
     * @param parentId 父目录 id
     * @return 子目录列表
     */
    List<FolderDO> findByUserIdAndParentIdAndDeletedFalse(Long userId, Long parentId);

    /**
     * 同层级下按名称查询目录，用于重名校验。
     *
     * @param userId   用户 id
     * @param parentId 父目录 id
     * @param name     目录名
     * @return 目录
     */
    FolderDO findFirstByUserIdAndParentIdAndNameAndDeletedFalse(Long userId, Long parentId, String name);

    /**
     * 按 id 与用户共同限定查询，天然阻止越权访问他人目录。
     *
     * @param id     目录 id
     * @param userId 用户 id
     * @return 目录
     */
    FolderDO findFirstByIdAndUserId(Long id, Long userId);
}
