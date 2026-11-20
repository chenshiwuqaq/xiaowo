package org.example.mynote.controller;

import jakarta.validation.Valid;
import org.example.mynote.common.Result;
import org.example.mynote.common.UserContext;
import org.example.mynote.dto.AiConfigSaveDTO;
import org.example.mynote.dto.AiTestDTO;
import org.example.mynote.service.AiConfigService;
import org.example.mynote.vo.AiConfigVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * AI 模型配置接口：选模型、填 Key、测连通、切默认。
 *
 * @author MyNote
 */
@RestController
@RequestMapping("/api/ai/config")
public class AiConfigController {

    private final AiConfigService aiConfigService;

    public AiConfigController(AiConfigService aiConfigService) {
        this.aiConfigService = aiConfigService;
    }

    /**
     * 模型列表（Key 为掩码）。
     *
     * @return 列表
     */
    @GetMapping("/list")
    public Result<List<AiConfigVO>> list() {
        return Result.ok(aiConfigService.list(UserContext.require()));
    }

    /**
     * 新增或修改模型配置。
     *
     * @param dto 入参
     * @return 回显
     */
    @PostMapping("/save")
    public Result<AiConfigVO> save(@Valid @RequestBody AiConfigSaveDTO dto) {
        return Result.ok(aiConfigService.save(UserContext.require(), dto));
    }

    /**
     * 删除配置。
     *
     * @param id 配置 id
     * @return 空
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        aiConfigService.delete(UserContext.require(), id);
        return Result.ok();
    }

    /**
     * 设为当前使用模型。
     *
     * @param id 配置 id
     * @return 回显
     */
    @PutMapping("/{id}/default")
    public Result<AiConfigVO> setDefault(@PathVariable Long id) {
        return Result.ok(aiConfigService.setDefault(UserContext.require(), id));
    }

    /**
     * 连通性测试。
     *
     * @param dto 入参
     * @return {ok, message, sample}
     */
    @PostMapping("/test")
    public Result<Map<String, Object>> test(@RequestBody AiTestDTO dto) {
        return Result.ok(aiConfigService.test(UserContext.require(), dto));
    }
}
