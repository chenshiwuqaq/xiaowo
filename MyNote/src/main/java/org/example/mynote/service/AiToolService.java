package org.example.mynote.service;

import org.example.mynote.dto.AiToolSaveDTO;
import org.example.mynote.vo.AiToolDetailVO;

import java.util.List;

/**
 * 用户自定义 AI 工具插件服务（声明式插件的增删改查）。
 *
 * @author MyNote
 */
public interface AiToolService {

    /**
     * 列出当前用户的全部插件（含停用），供管理页使用。
     *
     * @param userId 用户 id
     * @return 插件列表
     */
    List<AiToolDetailVO> mine(Long userId);

    /**
     * 新增或更新插件。
     *
     * @param userId 用户 id
     * @param dto    插件 manifest
     * @return 回显
     */
    AiToolDetailVO save(Long userId, AiToolSaveDTO dto);

    /**
     * 删除插件。
     *
     * @param userId 用户 id
     * @param id     插件主键
     */
    void delete(Long userId, Long id);

    /**
     * 启用 / 停用。
     *
     * @param userId 用户 id
     * @param id     插件主键
     * @return 回显
     */
    AiToolDetailVO toggle(Long userId, Long id);
}
