package org.example.mynote.controller;

import org.example.mynote.common.Result;
import org.example.mynote.common.UserContext;
import org.example.mynote.service.DiagramService;
import org.example.mynote.vo.DiagramVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 逻辑图查询接口。生成与保存挂在 /api/ai 下，这里只负责已存图的读取与管理。
 *
 * @author MyNote
 */
@RestController
@RequestMapping("/api/diagram")
public class DiagramController {

    private final DiagramService diagramService;

    public DiagramController(DiagramService diagramService) {
        this.diagramService = diagramService;
    }

    /**
     * 已保存的逻辑图列表。
     *
     * @return 列表
     */
    @GetMapping("/list")
    public Result<List<DiagramVO>> list() {
        return Result.ok(diagramService.list(UserContext.require()));
    }

    /**
     * 单张逻辑图。
     *
     * @param id 图 id
     * @return 图
     */
    @GetMapping("/{id}")
    public Result<DiagramVO> detail(@PathVariable Long id) {
        return Result.ok(diagramService.detail(UserContext.require(), id));
    }

    /**
     * 删除逻辑图。
     *
     * @param id 图 id
     * @return 空
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        diagramService.delete(UserContext.require(), id);
        return Result.ok();
    }
}
