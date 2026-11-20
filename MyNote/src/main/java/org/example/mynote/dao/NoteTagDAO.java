package org.example.mynote.dao;

import org.example.mynote.entity.NoteTagDO;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 笔记与标签关联 DAO。关联表不冗余存 user_id，鉴权由上层笔记归属校验保证。

 *
 * @author MyNote
 */
@Repository
public interface NoteTagDAO extends JpaRepository<NoteTagDO, Long> {

    /**
     * 查询笔记已绑定的标签关联。
     *
     * @param noteId 笔记 id
     * @return 关联列表
     */
    List<NoteTagDO> findByNoteId(Long noteId);

    /**
     * 查询标签下的笔记关联，用于按标签筛选笔记。
     *
     * @param tagId 标签 id
     * @return 关联列表
     */
    List<NoteTagDO> findByTagId(Long tagId);

    /**
     * 删除某笔记的全部标签关联（批量删除，避免"先查后删再插入"造成的 uk_note_tag 唯一键冲突）。
     *
     * @param noteId 笔记 id
     */
    @Modifying
    @Query("delete from NoteTagDO t where t.noteId = :noteId")
    void deleteByNoteId(@Param("noteId") Long noteId);

    /**
     * 批量删除笔记的标签关联，彻底删除笔记时使用。
     *
     * @param noteIds 笔记 id 集合
     */
    @Modifying
    @Query("delete from NoteTagDO t where t.noteId in :noteIds")
    void deleteByNoteIds(@Param("noteIds") List<Long> noteIds);
}
