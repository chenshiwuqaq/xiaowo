package org.example.mynote.controller;

import jakarta.validation.Valid;
import org.example.mynote.common.Result;
import org.example.mynote.dto.FolderMoveDTO;
import org.example.mynote.dto.FolderSaveDTO;
import org.example.mynote.service.FolderService;
import org.example.mynote.vo.FolderNodeVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 目录接口，路径与字段严格对齐 docs/API.md 第 4 节。
 *
 * <p>控制器只做参数搬运与信封包装：鉴权在拦截器、归属校验在 Service，
 * 这里出现 if-else 业务判断就说明分层被打破了。</p>
 *
 * @author MyNote
 */
@RestController
@RequestMapping("/api/folder")
public class FolderController {

    private final FolderService folderService;

    public FolderController(FolderService folderService) {
        this.folderService = folderService;
    }

    /**
     * 获取当前用户的完整目录树。
     *
     * @return 根目录节点列表
     */
    @GetMapping("/tree")
    public Result<List<FolderNodeVO>> tree() {
        return Result.ok(folderService.tree());
    }

    /**
     * 新建目录。
     *
     * @param dto 新建入参
     * @return 新目录节点
     */
    @PostMapping
    public Result<FolderNodeVO> create(@RequestBody @Valid FolderSaveDTO dto) {
        return Result.ok(folderService.create(dto));
    }

    /**
     * 重命名 / 改图标 / 调排序。
     *
     * @param id  目录 id
     * @param dto 编辑入参
     * @return 更新后的目录节点
     */
    @PutMapping("/{id}")
    public Result<FolderNodeVO> update(@PathVariable("id") Long id, @RequestBody @Valid FolderSaveDTO dto) {
        return Result.ok(folderService.update(id, dto));
    }

    /**
     * 移动目录到其他父级下。字面量路径 /move 优先于 /{id} 模板匹配，不会误入 update。
     *
     * @param dto 移动入参
     * @return 空数据
     */
    @PutMapping("/move")
    public Result<Void> move(@RequestBody @Valid FolderMoveDTO dto) {
        folderService.move(dto);
        return Result.ok();
    }

    /**
     * 删除目录（逻辑删除，其下笔记落未分类）。
     *
     * @param id 目录 id
     * @return 空数据
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        folderService.delete(id);
        return Result.ok();
    }
}
