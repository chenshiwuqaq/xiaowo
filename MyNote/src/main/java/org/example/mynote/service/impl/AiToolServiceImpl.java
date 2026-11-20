package org.example.mynote.service.impl;

import org.example.mynote.common.BizException;
import org.example.mynote.common.ResultCode;
import org.example.mynote.dao.AiToolDAO;
import org.example.mynote.dto.AiToolSaveDTO;
import org.example.mynote.entity.AiToolDO;
import org.example.mynote.service.AiToolService;
import org.example.mynote.vo.AiToolDetailVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 用户声明式插件服务实现。
 *
 * <p>只负责账号内插件的增删改查与 manifest 校验；真正执行由 {@code AiToolRegistry}
 * 走统一插件契约完成，二者不互相依赖。</p>
 *
 * @author MyNote
 */
@Service
public class AiToolServiceImpl implements AiToolService {

    /**
     * 插件标识只允许字母、数字、下划线与中划线，且不以数字开头的限制放松到「首字符为字母数字」，
     * 与前端提示保持一致
     */
    private static final Pattern TOOL_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}$");

    /**
     * 内置插件占用的短名，用户插件不可占用
     */
    private static final Set<String> RESERVED_IDS = Set.of("knowledge", "references");

    private final AiToolDAO aiToolDAO;

    public AiToolServiceImpl(AiToolDAO aiToolDAO) {
        this.aiToolDAO = aiToolDAO;
    }

    @Override
    public List<AiToolDetailVO> mine(Long userId) {
        return aiToolDAO.findByUserIdOrderBySortOrderAscIdAsc(userId).stream()
                .map(this::toDetailVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiToolDetailVO save(Long userId, AiToolSaveDTO dto) {
        validate(dto);
        AiToolDO entity = dto.getId() == null ? null : aiToolDAO.findFirstByIdAndUserId(dto.getId(), userId);
        if (entity == null) {
            // id 未传或失效：按 toolId 查找，撞上就当作更新，避免撞唯一键
            AiToolDO exists = aiToolDAO.findFirstByUserIdAndToolId(userId, dto.getToolId());
            entity = exists;
        }
        boolean isNew = entity == null;
        if (isNew) {
            entity = new AiToolDO();
            entity.setUserId(userId);
            entity.setCreateTime(LocalDateTime.now());
        }
        entity.setToolId(dto.getToolId().trim());
        entity.setName(dto.getName().trim());
        entity.setDescription(trimToNull(dto.getDescription()));
        entity.setRenderType(resolveRenderType(dto.getRenderType()));
        entity.setSystemPrompt(dto.getSystemPrompt());
        entity.setUserTemplate(trimToNull(dto.getUserTemplate()));
        entity.setInsertable(dto.getInsertable() == null ? Boolean.TRUE : dto.getInsertable());
        entity.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        entity.setUpdateTime(LocalDateTime.now());
        if (entity.getEnabled() == null) {
            entity.setEnabled(Boolean.TRUE);
        }
        return toDetailVO(aiToolDAO.save(entity));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long id) {
        require(userId, id);
        aiToolDAO.deleteByIdAndUser(id, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiToolDetailVO toggle(Long userId, Long id) {
        AiToolDO entity = require(userId, id);
        entity.setEnabled(!Boolean.TRUE.equals(entity.getEnabled()));
        entity.setUpdateTime(LocalDateTime.now());
        return toDetailVO(aiToolDAO.save(entity));
    }

    /**
     * manifest 校验，越界给中文提示。
     *
     * @param dto 入参
     */
    private void validate(AiToolSaveDTO dto) {
        String toolId = dto.getToolId() == null ? "" : dto.getToolId().trim();
        if (toolId.isEmpty() || !TOOL_ID_PATTERN.matcher(toolId).matches()) {
            throw new BizException("插件标识只能由字母、数字、下划线、中划线组成（1~64 位，首字符为字母或数字）");
        }
        if (RESERVED_IDS.contains(toolId) || toolId.startsWith("user:")) {
            throw new BizException("插件标识 " + toolId + " 与内置插件冲突，请换一个");
        }
        String renderType = resolveRenderType(dto.getRenderType());
        if (!"text".equals(renderType)) {
            throw new BizException("声明式插件当前只支持 text 输出，其他渲染类型请用内置插件或脚本插件");
        }
        if (dto.getSystemPrompt() == null || dto.getSystemPrompt().isBlank()) {
            throw new BizException("系统提示词不能为空：它是这个插件的角色与行为约束");
        }
    }

    private String resolveRenderType(String raw) {
        return raw == null || raw.isBlank() ? "text" : raw.trim();
    }

    private AiToolDO require(Long userId, Long id) {
        AiToolDO entity = aiToolDAO.findFirstByIdAndUserId(id, userId);
        if (entity == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return entity;
    }

    private AiToolDetailVO toDetailVO(AiToolDO entity) {
        AiToolDetailVO vo = new AiToolDetailVO();
        vo.setId(entity.getId());
        vo.setToolId(entity.getToolId());
        vo.setName(entity.getName());
        vo.setDescription(entity.getDescription());
        vo.setRenderType(entity.getRenderType());
        vo.setSystemPrompt(entity.getSystemPrompt());
        vo.setUserTemplate(entity.getUserTemplate());
        vo.setInsertable(entity.getInsertable());
        vo.setEnabled(entity.getEnabled());
        vo.setSortOrder(entity.getSortOrder());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    private static String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
