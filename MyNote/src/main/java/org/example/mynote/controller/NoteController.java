package org.example.mynote.controller;

import jakarta.validation.Valid;
import org.example.mynote.common.Result;
import org.example.mynote.dto.BatchIdsDTO;
import org.example.mynote.dto.BatchMoveDTO;
import org.example.mynote.dto.NoteSaveDTO;
import org.example.mynote.dto.NoteTopDTO;
import org.example.mynote.dto.NoteUpdateDTO;
import org.example.mynote.service.NoteService;
import org.example.mynote.vo.ImportResultVO;
import org.example.mynote.vo.NoteDetailVO;
import org.example.mynote.vo.NoteHistoryVO;
import org.example.mynote.vo.NoteVO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 笔记接口，路径与字段严格对齐 docs/API.md 第 5 节。
 *
 * <p>导出两个方法是全项目仅有的"不套 Result 信封"接口——文件流套 JSON 信封
 * 会让前端拿字节流当错误体解析，反而制造麻烦；其余接口一律 Result 包装。
 * 字面量路径（/list、/top、/batch…）在 Spring 的模式比较中优先于 /{id} 模板，
 * 不存在数字与单词抢路由的问题。</p>
 *
 * @author MyNote
 */
@RestController
@RequestMapping("/api/note")
public class NoteController {

    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    /**
     * 笔记列表：folderId 为空返回全部，0 返回未分类；keyword 非空走检索；tagId 非空按标签过滤。
     *
     * @param folderId 目录 id，可空
     * @param keyword  关键词，可空
     * @param tagId    标签 id，可空
     * @return 笔记列表（不含正文）
     */
    @GetMapping("/list")
    public Result<List<NoteVO>> list(@RequestParam(value = "folderId", required = false) Long folderId,
                                     @RequestParam(value = "keyword", required = false) String keyword,
                                     @RequestParam(value = "tagId", required = false) Long tagId) {
        return Result.ok(noteService.list(folderId, keyword, tagId));
    }

    /**
     * 回收站列表。
     *
     * @return 已删除笔记列表
     */
    @GetMapping("/trash")
    public Result<List<NoteVO>> trash() {
        return Result.ok(noteService.trash());
    }

    /**
     * 单篇详情（含正文与标签）。
     *
     * @param id 笔记 id
     * @return 详情
     */
    @GetMapping("/{id}")
    public Result<NoteDetailVO> detail(@PathVariable("id") Long id) {
        return Result.ok(noteService.detail(id));
    }

    /**
     * 新建笔记。
     *
     * @param dto 新建入参
     * @return 新笔记详情
     */
    @PostMapping
    public Result<NoteDetailVO> create(@RequestBody @Valid NoteSaveDTO dto) {
        return Result.ok(noteService.create(dto));
    }

    /**
     * 更新笔记；snapshot=true 时覆盖前先存历史版本。
     *
     * @param id  笔记 id
     * @param dto 更新入参
     * @return 更新后详情
     */
    @PutMapping("/{id}")
    public Result<NoteDetailVO> update(@PathVariable("id") Long id, @RequestBody @Valid NoteUpdateDTO dto) {
        return Result.ok(noteService.update(id, dto));
    }

    /**
     * 删除（进回收站）。
     *
     * @param id 笔记 id
     * @return 空数据
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        noteService.delete(id);
        return Result.ok();
    }

    /**
     * 从回收站恢复。
     *
     * @param id 笔记 id
     * @return 空数据
     */
    @PutMapping("/{id}/restore")
    public Result<Void> restore(@PathVariable("id") Long id) {
        noteService.restore(id);
        return Result.ok();
    }

    /**
     * 彻底删除（物理删除并清理全部关联）。
     *
     * @param id 笔记 id
     * @return 空数据
     */
    @DeleteMapping("/{id}/permanent")
    public Result<Void> permanent(@PathVariable("id") Long id) {
        noteService.permanent(id);
        return Result.ok();
    }

    /**
     * 置顶 / 取消置顶。
     *
     * @param dto 置顶入参
     * @return 空数据
     */
    @PutMapping("/top")
    public Result<Void> top(@RequestBody @Valid NoteTopDTO dto) {
        noteService.top(dto);
        return Result.ok();
    }

    /**
     * 批量移动。
     *
     * @param dto 批量移动入参
     * @return 空数据
     */
    @PutMapping("/batch-move")
    public Result<Void> batchMove(@RequestBody @Valid BatchMoveDTO dto) {
        noteService.batchMove(dto);
        return Result.ok();
    }

    /**
     * 批量进回收站。
     *
     * @param dto 批量 id 入参
     * @return 空数据
     */
    @DeleteMapping("/batch")
    public Result<Void> batchTrash(@RequestBody @Valid BatchIdsDTO dto) {
        noteService.batchTrash(dto);
        return Result.ok();
    }

    /**
     * 历史版本列表（不含正文）。
     *
     * @param id 笔记 id
     * @return 版本列表，时间倒序
     */
    @GetMapping("/{id}/history")
    public Result<List<NoteHistoryVO>> history(@PathVariable("id") Long id) {
        return Result.ok(noteService.history(id));
    }

    /**
     * 回滚到指定历史版本。
     *
     * @param historyId 历史版本 id
     * @return 回滚后的笔记详情
     */
    @PutMapping("/history/{historyId}/restore")
    public Result<NoteDetailVO> restoreHistory(@PathVariable("historyId") Long historyId) {
        return Result.ok(noteService.restoreHistory(historyId));
    }

    /**
     * 批量导入文档。folderId / noteType 作为 multipart 普通字段传入。
     *
     * @param files    文件数组
     * @param folderId 目标目录，可空
     * @param noteType 笔记类型，可空
     * @return 导入统计
     */
    @PostMapping("/import")
    public Result<ImportResultVO> importNotes(@RequestParam("files") MultipartFile[] files,
                                              @RequestParam(value = "folderId", required = false) Long folderId,
                                              @RequestParam(value = "noteType", required = false) Integer noteType) {
        return Result.ok(noteService.importNotes(files, folderId, noteType));
    }

    /**
     * 导出单篇为附件文件流（md 原样 / txt 去 Markdown 语法），不套 Result 信封。
     *
     * @param id     笔记 id
     * @param format md 或 txt，默认 md
     * @return 文件流响应
     */
    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> exportOne(@PathVariable("id") Long id,
                                            @RequestParam(value = "format", required = false,
                                                    defaultValue = "md") String format) {
        return noteService.exportOne(id, format);
    }

    /**
     * 批量导出 zip 文件流，不套 Result 信封。
     *
     * @param dto 批量入参（ids + format）
     * @return zip 响应
     */
    @PostMapping("/export")
    public ResponseEntity<byte[]> exportBatch(@RequestBody @Valid BatchIdsDTO dto) {
        return noteService.exportBatch(dto);
    }
}
