package org.example.mynote.service;

import org.example.mynote.dto.BatchIdsDTO;
import org.example.mynote.dto.BatchMoveDTO;
import org.example.mynote.dto.NoteSaveDTO;
import org.example.mynote.dto.NoteTopDTO;
import org.example.mynote.dto.NoteUpdateDTO;
import org.example.mynote.vo.ImportResultVO;
import org.example.mynote.vo.NoteDetailVO;
import org.example.mynote.vo.NoteHistoryVO;
import org.example.mynote.vo.NoteVO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 笔记业务服务。
 *
 * <p>承担笔记的增删改查、回收站、置顶、批量操作、历史版本与导入导出。
 * 所有方法以登录态用户为边界（内部 UserContext.require()），调用方传入的 id
 * 只做定位、不做鉴权依据。</p>
 *
 * @author MyNote
 */
public interface NoteService {

    /**
     * 笔记列表。keyword 非空走关键词检索；tagId 非空按标签过滤；folderId 为空返回全部。
     *
     * @param folderId 目录 id，null 表示不限目录，0 表示未分类
     * @param keyword  关键词，可空
     * @param tagId    标签 id，可空
     * @return 笔记列表项（含标签，不含正文）
     */
    List<NoteVO> list(Long folderId, String keyword, Long tagId);

    /**
     * 回收站列表。
     *
     * @return 已删除笔记列表项
     */
    List<NoteVO> trash();

    /**
     * 单篇详情（含正文与标签）。
     *
     * @param id 笔记 id
     * @return 详情视图
     */
    NoteDetailVO detail(Long id);

    /**
     * 新建笔记：补齐标题推断、字数与摘要统计、标签关联。
     *
     * @param dto 新建入参
     * @return 新笔记详情
     */
    NoteDetailVO create(NoteSaveDTO dto);

    /**
     * 更新笔记：按需写快照、记当日书写增量、重建标签。
     *
     * @param id  笔记 id
     * @param dto 更新入参
     * @return 更新后的详情
     */
    NoteDetailVO update(Long id, NoteUpdateDTO dto);

    /**
     * 删除（进回收站，逻辑删除）。
     *
     * @param id 笔记 id
     */
    void delete(Long id);

    /**
     * 从回收站恢复；原目录已删除时落到未分类。
     *
     * @param id 笔记 id
     */
    void restore(Long id);

    /**
     * 彻底删除：物理删笔记并清理标签、书写日志、历史版本、文献关联。
     *
     * @param id 笔记 id
     */
    void permanent(Long id);

    /**
     * 置顶 / 取消置顶。
     *
     * @param dto 置顶入参
     */
    void top(NoteTopDTO dto);

    /**
     * 批量移动到其他目录（folderId = 0 表示未分类）。
     *
     * @param dto 批量移动入参
     */
    void batchMove(BatchMoveDTO dto);

    /**
     * 批量进回收站。
     *
     * @param dto 批量 id 入参
     */
    void batchTrash(BatchIdsDTO dto);

    /**
     * 某笔记的历史版本列表（不含正文）。
     *
     * @param noteId 笔记 id
     * @return 版本列表，按时间倒序
     */
    List<NoteHistoryVO> history(Long noteId);

    /**
     * 回滚到指定历史版本：覆盖前先把当前内容另存一份快照。
     *
     * @param historyId 历史版本 id
     * @return 回滚后的笔记详情
     */
    NoteDetailVO restoreHistory(Long historyId);

    /**
     * 批量导入文档（.md/.markdown/.txt），单文件失败不影响整批。
     *
     * @param files    上传文件数组
     * @param folderId 目标目录，可空（未分类）
     * @param noteType 笔记类型，可空（默认随笔）
     * @return 导入结果统计
     */
    ImportResultVO importNotes(MultipartFile[] files, Long folderId, Integer noteType);

    /**
     * 导出单篇笔记为附件文件流。
     *
     * @param id     笔记 id
     * @param format md 原样 / txt 去 Markdown 语法
     * @return 可直接写回的响应实体（不套 Result 信封）
     */
    ResponseEntity<byte[]> exportOne(Long id, String format);

    /**
     * 批量导出为 zip，每篇一个条目，文件名取标题（清洗非法字符、重名加序号）。
     *
     * @param dto 批量入参（ids + format）
     * @return zip 文件流响应实体
     */
    ResponseEntity<byte[]> exportBatch(BatchIdsDTO dto);
}
