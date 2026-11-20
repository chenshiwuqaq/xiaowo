package org.example.mynote.dao;

import org.example.mynote.entity.NoteDO;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 笔记 DAO。
 *
 * <p>MySQL 5.7 的全文索引对中文需要 ngram 分词器，改动服务器配置代价较大，
 * 因此这里的检索统一走 LIKE 匹配（个人库数据量在万级以内足够），
 * 并在 SQL 层显式带上 user_id 条件，避免越权读取他人笔记。</p>
 *
 * @author MyNote
 */
@Repository
public interface NoteDAO extends JpaRepository<NoteDO, Long> {

    /**
     * 按 id 与用户查询笔记（含正文）。
     *
     * @param id     笔记 id
     * @param userId 用户 id
     * @return 笔记
     */
    NoteDO findFirstByIdAndUserId(Long id, Long userId);

    /**
     * 按 id 集合与用户批量查询，用于多篇笔记生成逻辑图。
     *
     * @param ids    笔记 id 集合
     * @param userId 用户 id
     * @return 笔记列表
     */
    List<NoteDO> findByIdInAndUserIdAndDeletedFalse(List<Long> ids, Long userId);

    /**
     * 目录下的笔记列表，置顶优先、按更新时间倒序。不返回正文，避免列表接口拖长文本。
     */
    @Query("select n.id as id, n.title as title, n.summary as summary, n.noteType as noteType, "
            + "n.folderId as folderId, n.wordCount as wordCount, n.top as top, "
            + "n.createTime as createTime, n.updateTime as updateTime from NoteDO n "
            + "where n.userId = :userId and n.deleted = false and n.folderId = :folderId "
            + "order by n.top desc, n.updateTime desc")
    List<NoteBrief> findBriefByFolder(@Param("userId") Long userId, @Param("folderId") Long folderId);

    /**
     * 全部正常笔记的简要信息，供侧栏搜索与 AI 组装上下文使用。
     */
    @Query("select n.id as id, n.title as title, n.summary as summary, n.noteType as noteType, "
            + "n.folderId as folderId, n.wordCount as wordCount, n.top as top, "
            + "n.createTime as createTime, n.updateTime as updateTime from NoteDO n "
            + "where n.userId = :userId and n.deleted = false order by n.updateTime desc")
    List<NoteBrief> findAllBrief(@Param("userId") Long userId);

    /**
     * 关键词检索：命中标题、摘要或正文，返回简要信息。
     */
    @Query("select n.id as id, n.title as title, n.summary as summary, n.noteType as noteType, "
            + "n.folderId as folderId, n.wordCount as wordCount, n.top as top, "
            + "n.createTime as createTime, n.updateTime as updateTime from NoteDO n "
            + "where n.userId = :userId and n.deleted = false "
            + "and (n.title like concat('%', :keyword, '%') or n.summary like concat('%', :keyword, '%') "
            + "or n.content like concat('%', :keyword, '%')) "
            + "order by n.updateTime desc")
    List<NoteBrief> search(@Param("userId") Long userId, @Param("keyword") String keyword);

    /**
     * 回收站列表。
     */
    @Query("select n.id as id, n.title as title, n.summary as summary, n.noteType as noteType, "
            + "n.folderId as folderId, n.wordCount as wordCount, n.top as top, "
            + "n.createTime as createTime, n.deleteTime as updateTime from NoteDO n "
            + "where n.userId = :userId and n.deleted = true order by n.deleteTime desc")
    List<NoteBrief> findTrash(@Param("userId") Long userId);

    /**
     * 取同目录下其它笔记的摘要，作为 AI 的邻域上下文。
     *
     * @param userId      用户 id
     * @param folderId    目录 id
     * @param excludeId   当前笔记 id
     * @param pageable    限制条数
     * @return 笔记列表（含摘要）
     */
    @Query("select n from NoteDO n where n.userId = :userId and n.deleted = false "
            + "and n.folderId = :folderId and n.id <> :excludeId order by n.updateTime desc")
    List<NoteDO> findSiblings(@Param("userId") Long userId, @Param("folderId") Long folderId,
                             @Param("excludeId") Long excludeId, Pageable pageable);

    /**
     * 最近更新的有效笔记，用于主页"继续写"卡片。
     */
    @Query("select n.id as id, n.title as title, n.summary as summary, n.noteType as noteType, "
            + "n.folderId as folderId, n.wordCount as wordCount, n.top as top, "
            + "n.createTime as createTime, n.updateTime as updateTime from NoteDO n "
            + "where n.userId = :userId and n.deleted = false order by n.updateTime desc")
    List<NoteBrief> findRecent(@Param("userId") Long userId, Pageable pageable);

    /**
     * 有效笔记数量与总字数（口径一：现存作品的字数）。返回 [数量, 总字数]。
     */
    @Query("select count(n.id), coalesce(sum(n.wordCount), 0) from NoteDO n "
            + "where n.userId = :userId and n.deleted = false")
    List<Object[]> countAndSumWords(@Param("userId") Long userId);

    /**
     * 各类型笔记数量，用于主页与设置页的分布展示。
     */
    @Query("select n.noteType, count(n.id) from NoteDO n where n.userId = :userId and n.deleted = false "
            + "group by n.noteType")
    List<Object[]> countByType(@Param("userId") Long userId);

    /**
     * 目录（含其子目录）下的有效笔记数量，用于删除目录前的占用检查。
     */
    @Query("select count(n.id) from NoteDO n where n.userId = :userId and n.deleted = false "
            + "and n.folderId in :folderIds")
    long countByFolders(@Param("userId") Long userId, @Param("folderIds") List<Long> folderIds);

    /**
     * 回收站中的笔记 id 列表，彻底删除时连带清理关联表。
     */
    @Query("select n.id from NoteDO n where n.userId = :userId and n.deleted = true")
    List<Long> findTrashIds(@Param("userId") Long userId);

    /**
     * 笔记简要视图投影，列表类接口只取这些字段。
     */
    interface NoteBrief {
        Long getId();

        String getTitle();

        String getSummary();

        Integer getNoteType();

        Long getFolderId();

        Integer getWordCount();

        Boolean getTop();

        java.time.LocalDateTime getCreateTime();

        java.time.LocalDateTime getUpdateTime();
    }
}
