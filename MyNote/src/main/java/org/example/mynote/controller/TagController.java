package org.example.mynote.controller;

import jakarta.validation.Valid;
import org.example.mynote.common.Result;
import org.example.mynote.dto.TagSaveDTO;
import org.example.mynote.dto.TagUpdateDTO;
import org.example.mynote.service.TagService;
import org.example.mynote.vo.TagVO;
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
 * 标签接口，路径与字段对齐 docs/API.md 第 6 节。
 *
 * @author MyNote
 */
@RestController
@RequestMapping("/api/tag")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    /**
     * 当前用户的标签列表，含每个标签的引用笔记数。
     *
     * @return 标签列表
     */
    @GetMapping("/list")
    public Result<List<TagVO>> list() {
        return Result.ok(tagService.list());
    }

    /**
     * 新建标签。
     *
     * @param dto 新建入参
     * @return 新标签
     */
    @PostMapping
    public Result<TagVO> create(@RequestBody @Valid TagSaveDTO dto) {
        return Result.ok(tagService.create(dto));
    }

    /**
     * 修改标签名 / 颜色。
     *
     * @param id  标签 id
     * @param dto 编辑入参
     * @return 更新后的标签
     */
    @PutMapping("/{id}")
    public Result<TagVO> update(@PathVariable("id") Long id, @RequestBody @Valid TagUpdateDTO dto) {
        return Result.ok(tagService.update(id, dto));
    }

    /**
     * 删除标签并解除其全部笔记关联。
     *
     * @param id 标签 id
     * @return 空数据
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        tagService.delete(id);
        return Result.ok();
    }
}
