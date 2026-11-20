package org.example.mynote.dao;

import org.example.mynote.entity.NoteReferenceDO;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 笔记与文献关联 DAO。

 *
 * @author MyNote
 */
@Repository
public interface NoteReferenceDAO extends JpaRepository<NoteReferenceDO, Long> {

    /**
     * 查询笔记已关联的文献。
     *
     * @param noteId 笔记 id
     * @return 关联列表
     */
    List<NoteReferenceDO> findByNoteId(Long noteId);

    /**
     * 判断是否已关联，避免重复绑定。
     *
     * @param noteId 笔记 id
     * @param refId  文献 id
     * @return true 表示已存在
     */
    boolean existsByNoteIdAndRefId(Long noteId, Long refId);

    /**
     * 解除单条关联。
     *
     * @param noteId 笔记 id
     * @param refId  文献 id
     */
    @Modifying
    @Query("delete from NoteReferenceDO r where r.noteId = :noteId and r.refId = :refId")
    void deleteByNoteAndRef(@Param("noteId") Long noteId, @Param("refId") Long refId);

    /**
     * 彻底删除笔记时清理关联。
     *
     * @param noteId 笔记 id
     */
    void deleteByNoteId(Long noteId);
}
