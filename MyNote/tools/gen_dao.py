#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""批量生成 DAO 接口（Spring Data JPA），保证注释与命名风格一致。"""
import io
import os

BASE = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                    "src", "main", "java", "org", "example", "mynote", "dao")

TPL = """package org.example.mynote.dao;

import org.example.mynote.entity.%s;
%s
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
%s
import java.util.List;

/**
 * %s *
 * @author MyNote
 */
@Repository
public interface %s extends JpaRepository<%s, Long> {

%s}
"""

IMPORT_JPA_EXTRA = ("import org.springframework.data.jpa.repository.Modifying;\n"
                    "import org.springframework.data.jpa.repository.Query;\n"
                    "import org.springframework.data.repository.query.Param;\n")
IMPORT_PAGEABLE = "import org.springframework.data.domain.Pageable;\n"
IMPORT_LOCALDATE = "import java.time.LocalDate;\n"

DAO_TAG = """    /**
     * 用户的全部标签。
     *
     * @param userId 用户 id
     * @return 标签列表
     */
    List<TagDO> findByUserIdOrderByIdAsc(Long userId);

    /**
     * 按名称查标签，用于"输入即复用"已有标签。
     *
     * @param userId 用户 id
     * @param name   标签名
     * @return 标签
     */
    TagDO findFirstByUserIdAndName(Long userId, String name);

    /**
     * 按 id 与用户共同限定，阻止越权操作他人标签。
     *
     * @param id     标签 id
     * @param userId 用户 id
     * @return 标签
     */
    TagDO findFirstByIdAndUserId(Long id, Long userId);
"""

DAO_NOTE_TAG = """    /**
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
     * 删除某笔记的全部标签关联。
     *
     * @param noteId 笔记 id
     */
    void deleteByNoteId(Long noteId);

    /**
     * 批量删除笔记的标签关联，彻底删除笔记时使用。
     *
     * @param noteIds 笔记 id 集合
     */
    @Modifying
    @Query("delete from NoteTagDO t where t.noteId in :noteIds")
    void deleteByNoteIds(@Param("noteIds") List<Long> noteIds);
"""

DAO_WRITING_LOG = """    /**
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
"""

DAO_KNOWLEDGE = """    /**
     * 查询全部知识卡片。语料量为数百条级，命中匹配在内存中完成比 SQL 里做关键词 LIKE 更可控。
     *
     * @return 卡片列表
     */
    List<KnowledgeCardDO> findAllByOrderByIdAsc();

    /**
     * 按分类查询。
     *
     * @param category 分类
     * @return 卡片列表
     */
    List<KnowledgeCardDO> findByCategory(String category);

    /**
     * 采纳一次，计数加一，可作为后续排序权重。
     *
     * @param id 卡片 id
     */
    @Modifying
    @Query("update KnowledgeCardDO k set k.refCount = k.refCount + 1 where k.id = :id")
    void increaseRefCount(@Param("id") Long id);
"""

DAO_REFERENCE = """    /**
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
"""

DAO_NOTE_REFERENCE = """    /**
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
"""

DAO_AI_CONFIG = """    /**
     * 用户的全部模型配置。
     *
     * @param userId 用户 id
     * @return 配置列表
     */
    List<AiConfigDO> findByUserIdOrderByCreateTimeAsc(Long userId);

    /**
     * 当前默认模型。
     *
     * @param userId 用户 id
     * @return 配置
     */
    AiConfigDO findFirstByUserIdAndDefaultConfigTrue(Long userId);

    /**
     * 按 id 与用户查询。
     *
     * @param id     配置 id
     * @param userId 用户 id
     * @return 配置
     */
    AiConfigDO findFirstByIdAndUserId(Long id, Long userId);

    /**
     * 同服务商同模型是否已配置，先于数据库唯一约束给出友好提示。
     *
     * @param userId   用户 id
     * @param provider 服务商标识
     * @param model    模型名
     * @return 配置
     */
    AiConfigDO findFirstByUserIdAndProviderCodeAndModelName(Long userId, String provider, String model);

    /**
     * 清除用户全部默认标记，再设置新的默认模型，保证同时只有一个默认。
     *
     * @param userId 用户 id
     */
    @Modifying
    @Query("update AiConfigDO c set c.defaultConfig = false where c.userId = :userId")
    void clearDefault(@Param("userId") Long userId);

    /**
     * 删除配置。
     *
     * @param id     配置 id
     * @param userId 用户 id
     */
    @Modifying
    @Query("delete from AiConfigDO c where c.id = :id and c.userId = :userId")
    void deleteByIdAndUser(@Param("id") Long id, @Param("userId") Long userId);
"""

DAO_AI_SESSION = """    /**
     * 用户会话列表，按最近活跃倒序。
     *
     * @param userId 用户 id
     * @param limit  分页参数，限制返回条数
     * @return 会话列表
     */
    @Query("select s from AiSessionDO s where s.userId = :userId order by s.updateTime desc")
    List<AiSessionDO> listByUser(@Param("userId") Long userId, Pageable limit);

    /**
     * 某篇笔记下的会话。
     *
     * @param userId 用户 id
     * @param noteId 笔记 id
     * @return 会话列表
     */
    List<AiSessionDO> findByUserIdAndNoteIdOrderByIdDesc(Long userId, Long noteId);

    /**
     * 按 id 与用户查询。
     *
     * @param id     会话 id
     * @param userId 用户 id
     * @return 会话
     */
    AiSessionDO findFirstByIdAndUserId(Long id, Long userId);
"""

DAO_AI_MESSAGE = """    /**
     * 会话内的全部消息，按 id 升序即时间正序。
     *
     * @param sessionId 会话 id
     * @return 消息列表
     */
    List<AiMessageDO> findBySessionIdOrderByIdAsc(Long sessionId);
"""

DAO_DIAGRAM = """    /**
     * 用户的逻辑图列表。
     *
     * @param userId 用户 id
     * @return 图列表
     */
    List<DiagramDO> findByUserIdOrderByCreateTimeDesc(Long userId);

    /**
     * 按 id 与用户查询。
     *
     * @param id     图 id
     * @param userId 用户 id
     * @return 图
     */
    DiagramDO findFirstByIdAndUserId(Long id, Long userId);
"""

DAO_NOTE_HISTORY = """    /**
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
"""

DAOS = [
    ("TagDO", "TagDAO", "标签 DAO。标签是全局标签池，同一用户下名称唯一。\n\n", DAO_TAG),
    ("NoteTagDO", "NoteTagDAO", "笔记与标签关联 DAO。关联表不冗余存 user_id，"
                                 "鉴权由上层笔记归属校验保证。\n\n", DAO_NOTE_TAG),
    ("WritingLogDO", "WritingLogDAO", "书写日志 DAO。按天聚合供热力图与连续打卡统计使用。\n\n", DAO_WRITING_LOG),
    ("KnowledgeCardDO", "KnowledgeCardDAO", "创作背景知识库 DAO。\n\n", DAO_KNOWLEDGE),
    ("ReferenceDO", "ReferenceDAO", "参考文献 DAO。本地文献库为离线兜底，"
                                    "联网可用时由 ScholarService 合并最新结果。\n\n", DAO_REFERENCE),
    ("NoteReferenceDO", "NoteReferenceDAO", "笔记与文献关联 DAO。\n\n", DAO_NOTE_REFERENCE),
    ("AiConfigDO", "AiConfigDAO", "AI 模型配置 DAO。api_key 字段存密文，DAO 层不解密。\n\n", DAO_AI_CONFIG),
    ("AiSessionDO", "AiSessionDAO", "AI 会话 DAO。\n\n", DAO_AI_SESSION),
    ("AiMessageDO", "AiMessageDAO", "AI 消息 DAO。\n\n", DAO_AI_MESSAGE),
    ("DiagramDO", "DiagramDAO", "逻辑图 DAO，存 Mermaid 源码。\n\n", DAO_DIAGRAM),
    ("NoteHistoryDO", "NoteHistoryDAO", "笔记历史版本 DAO。\n\n", DAO_NOTE_HISTORY),
]


def main():
    if not os.path.isdir(BASE):
        os.makedirs(BASE)
    for entity, name, doc, methods in DAOS:
        pageable = IMPORT_PAGEABLE if "Pageable" in methods else ""
        jpa_extra = IMPORT_JPA_EXTRA if ("@Modifying" in methods or "@Query" in methods) else ""
        imports = (pageable + jpa_extra).replace(IMPORT_JPA_EXTRA, "") if jpa_extra else pageable
        # 额外 import 直接拼在 entity import 之后
        head = "import org.example.mynote.entity.%s;" % entity
        extra = ""
        if pageable:
            extra += "\n" + pageable.rstrip("\n")
        if jpa_extra:
            extra += "\n" + jpa_extra.rstrip("\n")
        body = methods
        if "LocalDate" in methods:
            dateimp = IMPORT_LOCALDATE.rstrip("\n")
        else:
            dateimp = ""
        content = (
                "package org.example.mynote.dao;\n\n"
                + head + extra + "\n"
                + "import org.springframework.data.jpa.repository.JpaRepository;\n"
                + "import org.springframework.stereotype.Repository;\n"
                + ("\n" + dateimp if dateimp else "") + "\n"
                + "import java.util.List;\n\n"
                + "/**\n * " + doc + " *\n * @author MyNote\n */\n"
                + "@Repository\npublic interface %s extends JpaRepository<%s, Long> {\n\n" % (name, entity)
                + body + "}\n"
        )
        io.open(os.path.join(BASE, name + ".java"), "w", encoding="utf-8", newline="\n").write(content)
        print("wrote", name)


if __name__ == "__main__":
    main()
