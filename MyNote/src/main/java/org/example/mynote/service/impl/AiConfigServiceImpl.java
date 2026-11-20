package org.example.mynote.service.impl;

import org.example.mynote.ai.AiRuntimeConfig;
import org.example.mynote.ai.OpenAiClient;
import org.example.mynote.common.BizException;
import org.example.mynote.common.ResultCode;
import org.example.mynote.dao.AiConfigDAO;
import org.example.mynote.dto.AiConfigSaveDTO;
import org.example.mynote.dto.AiTestDTO;
import org.example.mynote.entity.AiConfigDO;
import org.example.mynote.service.AiConfigService;
import org.example.mynote.util.AesUtil;
import org.example.mynote.vo.AiConfigVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 模型配置服务实现。
 *
 * <p>密钥处理是这里唯一需要谨慎的部分：入参明文只在内存中短暂存在，落库前 AES 加密，
 * 回显时只给掩码；更新时"空串 = 保持原值"，避免前端把掩码串当新 Key 提交后覆盖掉真值。</p>
 *
 * @author MyNote
 */
@Service
public class AiConfigServiceImpl implements AiConfigService {

    private static final Logger log = LoggerFactory.getLogger(AiConfigServiceImpl.class);

    /**
     * 掩码串标记，前端回传带 **** 的值一律视为"不修改"
     */
    private static final String MASK_MARK = "****";

    private static final BigDecimal TEMPERATURE_MIN = BigDecimal.ZERO;

    private static final BigDecimal TEMPERATURE_MAX = new BigDecimal("2.00");

    private static final int MAX_TOKENS_LIMIT = 32000;

    private final AiConfigDAO aiConfigDAO;

    private final AesUtil aesUtil;

    private final OpenAiClient openAiClient;

    public AiConfigServiceImpl(AiConfigDAO aiConfigDAO, AesUtil aesUtil, OpenAiClient openAiClient) {
        this.aiConfigDAO = aiConfigDAO;
        this.aesUtil = aesUtil;
        this.openAiClient = openAiClient;
    }

    @Override
    public List<AiConfigVO> list(Long userId) {
        List<AiConfigDO> configs = aiConfigDAO.findByUserIdOrderByCreateTimeAsc(userId);
        List<AiConfigVO> result = new ArrayList<>(configs.size());
        for (AiConfigDO config : configs) {
            result.add(toVO(config));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiConfigVO save(Long userId, AiConfigSaveDTO dto) {
        validateRange(dto);
        AiConfigDO entity = dto.getId() == null ? null : aiConfigDAO.findFirstByIdAndUserId(dto.getId(), userId);
        if (entity == null) {
            AiConfigDO exists = aiConfigDAO.findFirstByUserIdAndProviderCodeAndModelName(userId,
                    dto.getProviderCode(), dto.getModelName());
            if (exists != null) {
                // id 未传但同模型已配置：当作更新处理，避免撞数据库唯一键报出难懂的错
                entity = exists;
            }
        }
        boolean isNew = entity == null;
        if (isNew) {
            entity = new AiConfigDO();
            entity.setUserId(userId);
            entity.setCreateTime(LocalDateTime.now());
        }
        entity.setProviderCode(dto.getProviderCode());
        entity.setProviderName(blankToDefault(dto.getProviderName(), providerNameOf(dto.getProviderCode())));
        entity.setBaseUrl(normalizeBaseUrl(dto.getBaseUrl()));
        entity.setModelName(dto.getModelName().trim());
        entity.setTemperature(dto.getTemperature() == null ? new BigDecimal("0.70") : dto.getTemperature());
        entity.setMaxTokens(dto.getMaxTokens() == null || dto.getMaxTokens() <= 0 ? 2048 : dto.getMaxTokens());
        entity.setUpdateTime(LocalDateTime.now());
        if (entity.getEnabled() == null) {
            entity.setEnabled(Boolean.TRUE);
        }
        // is_default / is_enabled 在建表时是 NOT NULL，Hibernate 会把 null 显式写进去，
        // 所以新建路径必须给默认值，否则第一次保存配置就会撞约束
        if (entity.getDefaultConfig() == null) {
            entity.setDefaultConfig(Boolean.FALSE);
        }
        applyApiKey(entity, dto.getApiKey());

        AiConfigDO saved = aiConfigDAO.save(entity);
        // 首个配置自动成为默认，省掉一次"保存后再点设为默认"的操作
        if (Boolean.TRUE.equals(saved.getDefaultConfig()) || (isNew && noDefaultYet(userId))) {
            aiConfigDAO.clearDefault(userId);
            saved.setDefaultConfig(true);
            aiConfigDAO.save(saved);
        }
        return toVO(saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long id) {
        AiConfigDO config = require(userId, id);
        boolean wasDefault = Boolean.TRUE.equals(config.getDefaultConfig());
        aiConfigDAO.deleteByIdAndUser(id, userId);
        if (wasDefault) {
            promoteFirstRemaining(userId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiConfigVO setDefault(Long userId, Long id) {
        AiConfigDO config = require(userId, id);
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            throw new BizException("该模型已停用，先启用后再设为默认");
        }
        if (isBlank(config.getApiKey())) {
            throw new BizException("该模型还没有填写 API Key，无法设为默认");
        }
        aiConfigDAO.clearDefault(userId);
        config.setDefaultConfig(true);
        config.setUpdateTime(LocalDateTime.now());
        return toVO(aiConfigDAO.save(config));
    }

    @Override
    public AiRuntimeConfig currentRuntime(Long userId) {
        AiConfigDO config = aiConfigDAO.findFirstByUserIdAndDefaultConfigTrue(userId);
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            AiConfigDO fallback = firstUsable(userId);
            if (fallback == null) {
                return null;
            }
            config = fallback;
        }
        String plain = aesUtil.decrypt(config.getApiKey());
        if (plain == null || plain.isEmpty()) {
            return null;
        }
        return new AiRuntimeConfig(config.getBaseUrl(), config.getModelName(), plain, config.getTemperature(),
                config.getMaxTokens(), config.getProviderName());
    }

    @Override
    public boolean available(Long userId) {
        return currentRuntime(userId) != null;
    }

    @Override
    public Map<String, Object> test(Long userId, AiTestDTO dto) {
        Map<String, Object> result = new HashMap<>(4);
        AiRuntimeConfig runtime = resolveForTest(userId, dto);
        if (runtime == null) {
            result.put("ok", false);
            result.put("message", "地址、模型名与 API Key 需要填齐");
            return result;
        }
        try {
            List<OpenAiClient.Message> messages = new ArrayList<>(2);
            messages.add(new OpenAiClient.Message("system", "只回复两个字：通了"));
            messages.add(new OpenAiClient.Message("user", "连接测试"));
            String answer = openAiClient.chatOnce(runtime, messages);
            result.put("ok", true);
            result.put("message", "连接成功");
            result.put("sample", answer.length() > 40 ? answer.substring(0, 40) : answer);
            result.put("model", runtime.getModelName());
        } catch (BizException e) {
            log.info("模型连通性测试失败: {}", e.getMessage());
            result.put("ok", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 解析测试用的运行时配置：优先用已存配置，其次用表单临时值（掩码 Key 沿用已存密文）。
     *
     * @param userId 用户 id
     * @param dto    测试入参
     * @return 配置，信息不全时返回 null
     */
    private AiRuntimeConfig resolveForTest(Long userId, AiTestDTO dto) {
        if (dto.getId() != null) {
            AiConfigDO config = require(userId, dto.getId());
            String plain = aesUtil.decrypt(config.getApiKey());
            if (plain == null) {
                return null;
            }
            return new AiRuntimeConfig(config.getBaseUrl(), config.getModelName(), plain, config.getTemperature(),
                    config.getMaxTokens(), config.getProviderName());
        }
        if (isBlank(dto.getBaseUrl()) || isBlank(dto.getModelName())) {
            return null;
        }
        String key = dto.getApiKey();
        if (key != null && key.contains(MASK_MARK)) {
            // 表单里回显的是掩码，测试时补回真实 Key
            AiConfigDO stored = aiConfigDAO.findFirstByIdAndUserId(dto.getId(), userId);
            key = stored == null ? null : aesUtil.decrypt(stored.getApiKey());
        }
        if (isBlank(key)) {
            return null;
        }
        return new AiRuntimeConfig(normalizeBaseUrl(dto.getBaseUrl()), dto.getModelName().trim(), key,
                new BigDecimal("0.70"), 64, "测试");
    }

    /**
     * 处理 API Key 字段的三种情况：未传、传掩码、传新值。
     *
     * @param entity  待保存实体
     * @param input   入参明文
     */
    private void applyApiKey(AiConfigDO entity, String input) {
        if (input == null) {
            return;
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty() || trimmed.contains(MASK_MARK)) {
            // 空串或掩码都视为"不改"，第一次配置时保持 null
            return;
        }
        entity.setApiKey(aesUtil.encrypt(trimmed));
    }

    /**
     * 数值范围校验，越界直接给中文提示，避免调用模型时被上游拒绝。
     *
     * @param dto 入参
     */
    private void validateRange(AiConfigSaveDTO dto) {
        if (dto.getTemperature() != null && (dto.getTemperature().compareTo(TEMPERATURE_MIN) < 0
                || dto.getTemperature().compareTo(TEMPERATURE_MAX) > 0)) {
            throw new BizException("采样温度要在 0 到 2 之间");
        }
        if (dto.getMaxTokens() != null && (dto.getMaxTokens() <= 0 || dto.getMaxTokens() > MAX_TOKENS_LIMIT)) {
            throw new BizException("最大输出 token 要在 1 到 " + MAX_TOKENS_LIMIT + " 之间");
        }
    }

    /**
     * 去掉 baseUrl 结尾斜杠，防止拼出 //chat/completions。
     *
     * @param baseUrl 原始地址
     * @return 规范化地址
     */
    private String normalizeBaseUrl(String baseUrl) {
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            throw new BizException("接口地址需要以 http:// 或 https:// 开头");
        }
        return trimmed;
    }

    private AiConfigDO require(Long userId, Long id) {
        AiConfigDO config = aiConfigDAO.findFirstByIdAndUserId(id, userId);
        if (config == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return config;
    }

    private boolean noDefaultYet(Long userId) {
        return aiConfigDAO.findFirstByUserIdAndDefaultConfigTrue(userId) == null;
    }

    private AiConfigDO firstUsable(Long userId) {
        for (AiConfigDO config : aiConfigDAO.findByUserIdOrderByCreateTimeAsc(userId)) {
            if (Boolean.TRUE.equals(config.getEnabled()) && !isBlank(config.getApiKey())) {
                return config;
            }
        }
        return null;
    }

    /**
     * 默认模型被删后，把剩下第一个可用配置提为默认，保证列表里始终有当前模型。
     *
     * @param userId 用户 id
     */
    private void promoteFirstRemaining(Long userId) {
        AiConfigDO next = firstUsable(userId);
        if (next == null) {
            return;
        }
        aiConfigDAO.clearDefault(userId);
        next.setDefaultConfig(true);
        aiConfigDAO.save(next);
    }

    /**
     * 服务商展示名兜底，前端只传 code 时也能显示中文名。
     *
     * @param code 标识
     * @return 展示名
     */
    private String providerNameOf(String code) {
        if (code == null) {
            return "自定义";
        }
        switch (code) {
            case "deepseek":
                return "DeepSeek";
            case "openai":
                return "OpenAI";
            case "qianwen":
                return "通义千问";
            case "kimi":
                return "Kimi";
            case "zhipu":
                return "智谱 GLM";
            default:
                return "自定义";
        }
    }

    /**
     * 实体转回显对象，明文 Key 不出这个方法。
     *
     * @param config 配置
     * @return 回显
     */
    private AiConfigVO toVO(AiConfigDO config) {
        AiConfigVO vo = new AiConfigVO();
        vo.setId(config.getId());
        vo.setProviderCode(config.getProviderCode());
        vo.setProviderName(config.getProviderName());
        vo.setBaseUrl(config.getBaseUrl());
        vo.setModelName(config.getModelName());
        String plain = aesUtil.decrypt(config.getApiKey());
        vo.setApiKeyMask(plain == null ? null : aesUtil.mask(plain));
        vo.setHasKey(plain != null && !plain.isEmpty());
        vo.setTemperature(config.getTemperature());
        vo.setMaxTokens(config.getMaxTokens());
        vo.setDefaultConfig(config.getDefaultConfig());
        vo.setEnabled(config.getEnabled());
        return vo;
    }

    private static boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }

    private static String blankToDefault(String text, String fallback) {
        return isBlank(text) ? fallback : text.trim();
    }
}
