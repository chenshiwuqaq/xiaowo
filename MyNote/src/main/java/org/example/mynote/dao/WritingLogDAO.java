package org.example.mynote.dao;

import org.example.mynote.entity.WritingLogDO;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * 书写日志 DAO。按天聚合供热力图与连续打卡统计使用。

 *
 * @author MyNote
 */
@Repository
public interface WritingLogDAO extends JpaRepository<WritingLogDO, Long> {

    /**
     * 区间内的书写日志。
     *
     * @param userId 用户 id
     * @param start  起始日期（含）
     * @param end    结束日期（含）
     * @return 日志列表
     */
    List<WritingLogDO> findByUserIdAndLogDateBetweenOrderByLogDateAsc(Long userId, LocalDate start,
                                                                     LocalDate end);

    /**
     * 当日累计字数增量。
     *
     * @param userId 用户 id
     * @param date   日期
     * @return 当日字数
     */
    @Query("select coalesce(sum(w.wordDelta), 0) from WritingLogDO w where w.userId = :userId "
            + "and w.logDate = :date")
    int sumOfDay(@Param("userId") Long userId, @Param("date") LocalDate date);

    /**
     * 有书写行为的日期集合（倒序），用于计算连续打卡天数。
     *
     * @param userId 用户 id
     * @return 日期列表
     */
    @Query("select distinct w.logDate from WritingLogDO w where w.userId = :userId and w.wordDelta > 0 "
            + "order by w.logDate desc")
    List<LocalDate> distinctWritingDates(@Param("userId") Long userId);

    /**
     * 全部日志字数合计（口径二：书写过程量，含被删改的字）。
     *
     * @param userId 用户 id
     * @return 合计
     */
    @Query("select coalesce(sum(w.wordDelta), 0) from WritingLogDO w where w.userId = :userId")
    long sumAll(@Param("userId") Long userId);

    /**
     * 彻底删除笔记时清理其书写日志。
     *
     * @param noteId 笔记 id
     */
    @Modifying
    @Query("delete from WritingLogDO w where w.noteId = :noteId")
    void deleteByNoteId(@Param("noteId") Long noteId);
}
