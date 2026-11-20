package org.example.mynote.dao;

import org.example.mynote.entity.NoteHistoryDO;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 笔记历史版本 DAO。

 *
 * @author MyNote
 */
@Repository
public interface NoteHistoryDAO extends JpaRepository<NoteHistoryDO, Long> {

    /**
     * 某笔记的历史版本列表（倒序）。
     *
     * @param noteId 笔记 id
     * @return 版本列表
     */
    List<NoteHistoryDO> findByNoteIdOrderByCreateTimeDesc(Long noteId);

    /**
     * 按 id 与用户查询单条版本。
     *
     * @param id     版本 id
     * @param userId 用户 id
     * @return 版本
     */
    NoteHistoryDO findFirstByIdAndUserId(Long id, Long userId);

    /**
     * 只保留最近 keep 条，避免历史表无限膨胀。
     *
     * @param noteId 笔记 id
     * @param keep   保留条数
     */
    @Modifying
    @Query(value = "delete from mn_note_history where note_id = :noteId and id not in "
            + "(select id from (select id from mn_note_history where note_id = :noteId "
            + "order by create_time desc limit :keep) t)", nativeQuery = true)
    void deleteOlderThan(@Param("noteId") Long noteId, @Param("keep") int keep);

    /**
     * 彻底删除笔记时清理历史。
     *
     * @param noteId 笔记 id
     */
    void deleteByNoteId(Long noteId);
}
