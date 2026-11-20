package org.example.mynote.service;

import org.example.mynote.dto.DiagramGenerateDTO;
import org.example.mynote.dto.DiagramSaveDTO;
import org.example.mynote.vo.DiagramVO;

import java.util.List;

/**
 * 逻辑图服务：把多篇笔记的关系画成 Mermaid 图。
 *
 * @author MyNote
 */
public interface DiagramService {

    /**
     * 按选中的多篇笔记生成图。useAi=false 时用本地算法，无 API Key 也能出图。
     *
     * @param userId 用户 id
     * @param dto    生成参数
     * @return 生成的图（未落库）
     */
    DiagramVO generate(Long userId, DiagramGenerateDTO dto);

    /**
     * 保存生成结果。
     *
     * @param userId 用户 id
     * @param dto    图内容
     * @return 落库后的图
     */
    DiagramVO save(Long userId, DiagramSaveDTO dto);

    /**
     * 用户的图列表。
     *
     * @param userId 用户 id
     * @return 列表
     */
    List<DiagramVO> list(Long userId);

    /**
     * 单张图。
     *
     * @param userId 用户 id
     * @param id     图 id
     * @return 图
     */
    DiagramVO detail(Long userId, Long id);

    /**
     * 删除图。
     *
     * @param userId 用户 id
     * @param id     图 id
     */
    void delete(Long userId, Long id);
}
