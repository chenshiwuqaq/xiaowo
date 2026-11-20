package org.example.mynote.service;

import org.example.mynote.ai.AiRuntimeConfig;
import org.example.mynote.dto.AiConfigSaveDTO;
import org.example.mynote.dto.AiTestDTO;
import org.example.mynote.vo.AiConfigVO;

import java.util.List;
import java.util.Map;

/**
 * AI 模型配置服务。
 *
 * @author MyNote
 */
public interface AiConfigService {

    /**
     * 列出用户的全部模型配置（Key 掩码）。
     *
     * @param userId 用户 id
     * @return 配置列表
     */
    List<AiConfigVO> list(Long userId);

    /**
     * 新增或更新配置。
     *
     * @param userId 用户 id
     * @param dto    配置内容
     * @return 回显
     */
    AiConfigVO save(Long userId, AiConfigSaveDTO dto);

    /**
     * 删除配置。
     *
     * @param userId 用户 id
     * @param id     配置 id
     */
    void delete(Long userId, Long id);

    /**
     * 设为当前使用模型。
     *
     * @param userId 用户 id
     * @param id     配置 id
     * @return 回显
     */
    AiConfigVO setDefault(Long userId, Long id);

    /**
     * 取当前默认模型的运行时配置。
     *
     * @param userId 用户 id
     * @return 可用配置；未配置或未填 Key 时返回 null
     */
    AiRuntimeConfig currentRuntime(Long userId);

    /**
     * 是否已经具备调用模型的能力。
     *
     * @param userId 用户 id
     * @return true 表示可调用
     */
    boolean available(Long userId);

    /**
     * 连通性测试。用一句最短提示打通链路，失败时返回可诊断的原因。
     *
     * @param userId 用户 id
     * @param dto    测试参数
     * @return {ok, message, sample}
     */
    Map<String, Object> test(Long userId, AiTestDTO dto);
}
