package org.example.mynote.service.impl;

import org.example.mynote.common.BizException;
import org.example.mynote.common.PageResult;
import org.example.mynote.common.ResultCode;
import org.example.mynote.dao.AiToolDAO;
import org.example.mynote.dao.MarketCommentDAO;
import org.example.mynote.dao.MarketLikeDAO;
import org.example.mynote.dao.MarketPluginDAO;
import org.example.mynote.dao.MarketUseDAO;
import org.example.mynote.dao.UserDAO;
import org.example.mynote.dto.AiToolSaveDTO;
import org.example.mynote.dto.MarketCommentSaveDTO;
import org.example.mynote.dto.MarketPluginSaveDTO;
import org.example.mynote.entity.AiToolDO;
import org.example.mynote.entity.MarketCommentDO;
import org.example.mynote.entity.MarketLikeDO;
import org.example.mynote.entity.MarketPluginDO;
import org.example.mynote.entity.MarketUseDO;
import org.example.mynote.entity.UserDO;
import org.example.mynote.service.AiToolService;
import org.example.mynote.service.MarketService;
import org.example.mynote.vo.MarketCommentVO;
import org.example.mynote.vo.MarketLikeVO;
import org.example.mynote.vo.MarketPluginDetailVO;
import org.example.mynote.vo.MarketPluginVO;
import org.example.mynote.vo.MarketRankingsVO;
import org.example.mynote.vo.MarketUseVO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 插件市场服务实现。
 *
 * <p>市场条目是插件 manifest 的快照，跨账号可见。点赞 / 评论 / 使用明细与反规范化计数
 * 在同一事务内维护，保证排行榜排序与卡片数字一致；「使用」会把 manifest 装进当前用户的
 * 声明式插件（复用 {@link AiToolService}，不覆盖用户已有的同标识插件）。</p>
 *
 * @author MyNote
 */
@Service
public class MarketServiceImpl implements MarketService {

    /** 分类：全部（检索参数用，非真实分类） */
    private static final int CATEGORY_ALL = -1;

    private static final Pattern TOOL_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}$");

    private final MarketPluginDAO marketPluginDAO;

    private final MarketLikeDAO marketLikeDAO;

    private final MarketCommentDAO marketCommentDAO;

    private final MarketUseDAO marketUseDAO;

    private final AiToolDAO aiToolDAO;

    private final AiToolService aiToolService;

    private final UserDAO userDAO;

    public MarketServiceImpl(MarketPluginDAO marketPluginDAO, MarketLikeDAO marketLikeDAO,
                             MarketCommentDAO marketCommentDAO, MarketUseDAO marketUseDAO,
                             AiToolDAO aiToolDAO, AiToolService aiToolService, UserDAO userDAO) {
        this.marketPluginDAO = marketPluginDAO;
        this.marketLikeDAO = marketLikeDAO;
        this.marketCommentDAO = marketCommentDAO;
        this.marketUseDAO = marketUseDAO;
        this.aiToolDAO = aiToolDAO;
        this.aiToolService = aiToolService;
        this.userDAO = userDAO;
    }

    @Override
    public PageResult<MarketPluginVO> list(Long userId, String keyword, Integer category, String sort,
                                           int pageNo, int pageSize) {
        String kw = keyword == null ? "" : keyword.trim();
        int cat = category == null ? CATEGORY_ALL : category;
        int size = Math.max(1, Math.min(pageSize, 100));
        int page = Math.max(0, pageNo - 1);
        Page<MarketPluginDO> result = marketPluginDAO.searchPublished(kw, cat, PageRequest.of(page, size, sortBy(sort)));
        List<MarketPluginVO> list = toVOList(userId, result.getContent());
        return new PageResult<>(list, result.getTotalElements(), page + 1, size);
    }

    @Override
    public MarketRankingsVO rankings(Long userId, int size) {
        int n = Math.max(1, Math.min(size, 50));
        MarketRankingsVO vo = new MarketRankingsVO();
        vo.setByLike(top(userId, n, "likeCount"));
        vo.setByComment(top(userId, n, "commentCount"));
        vo.setByUse(top(userId, n, "useCount"));
        return vo;
    }

    @Override
    public List<MarketPluginVO> random(Long userId, String keyword, Integer category, int size) {
        String kw = keyword == null ? "" : keyword.trim();
        int cat = category == null ? CATEGORY_ALL : category;
        int n = Math.max(1, Math.min(size, 100));
        List<Long> ids = marketPluginDAO.findPublishedIds(kw, cat);
        Collections.shuffle(ids);
        if (ids.size() > n) {
            ids = new ArrayList<>(ids.subList(0, n));
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, MarketPluginDO> byId = marketPluginDAO.findAllById(ids).stream()
                .collect(Collectors.toMap(MarketPluginDO::getId, x -> x));
        List<MarketPluginDO> ordered = ids.stream().map(byId::get).filter(Objects::nonNull)
                .collect(Collectors.toList());
        return toVOList(userId, ordered);
    }

    @Override
    public MarketPluginDetailVO detail(Long userId, Long id) {
        MarketPluginDO p = requirePublished(id);
        Set<Long> liked = likeIds(userId, List.of(id));
        Set<Long> used = useIds(userId, List.of(id));
        List<MarketCommentVO> comments = marketCommentDAO.findByPluginIdOrderByCreateTimeDesc(id).stream()
                .map(this::toCommentVO)
                .collect(Collectors.toList());
        return toDetailVO(p, liked, used, comments);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MarketPluginVO publish(Long userId, MarketPluginSaveDTO dto) {
        dto.setId(null);
        return save(userId, dto, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MarketPluginVO update(Long userId, Long id, MarketPluginSaveDTO dto) {
        dto.setId(id);
        return save(userId, dto, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long id) {
        MarketPluginDO p = marketPluginDAO.findFirstByIdAndAuthorId(id, userId);
        if (p == null) {
            throw new BizException(ResultCode.NOT_FOUND, "没有找到你的这条发布");
        }
        marketLikeDAO.deleteByPluginId(id);
        marketCommentDAO.deleteByPluginId(id);
        marketUseDAO.deleteByPluginId(id);
        marketPluginDAO.delete(p);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MarketPluginVO setStatus(Long userId, Long id, boolean published) {
        MarketPluginDO p = marketPluginDAO.findFirstByIdAndAuthorId(id, userId);
        if (p == null) {
            throw new BizException(ResultCode.NOT_FOUND, "没有找到你的这条发布");
        }
        p.setStatus(published ? 1 : 0);
        p.setUpdateTime(LocalDateTime.now());
        return toVO(marketPluginDAO.save(p), Set.of(), Set.of());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MarketLikeVO like(Long userId, Long id) {
        MarketPluginDO p = requirePublished(id);
        MarketLikeDO existing = marketLikeDAO.findFirstByPluginIdAndUserId(id, userId);
        boolean liked;
        if (existing == null) {
            MarketLikeDO like = new MarketLikeDO();
            like.setPluginId(id);
            like.setUserId(userId);
            like.setCreateTime(LocalDateTime.now());
            marketLikeDAO.save(like);
            p.setLikeCount((p.getLikeCount() == null ? 0 : p.getLikeCount()) + 1);
            liked = true;
        } else {
            marketLikeDAO.deleteByPluginIdAndUserId(id, userId);
            p.setLikeCount(Math.max(0, (p.getLikeCount() == null ? 0 : p.getLikeCount()) - 1));
            liked = false;
        }
        p.setUpdateTime(LocalDateTime.now());
        marketPluginDAO.save(p);
        MarketLikeVO vo = new MarketLikeVO();
        vo.setLiked(liked);
        vo.setLikeCount(p.getLikeCount());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MarketUseVO use(Long userId, Long id) {
        MarketPluginDO p = requirePublished(id);
        // 装进我的插件：已有同标识插件则不覆盖，只视为「已安装」
        AiToolDO existing = aiToolDAO.findFirstByUserIdAndToolId(userId, p.getToolId());
        if (existing == null) {
            AiToolSaveDTO dto = new AiToolSaveDTO();
            dto.setToolId(p.getToolId());
            dto.setName(p.getName());
            dto.setDescription(p.getDescription());
            dto.setRenderType(p.getRenderType());
            dto.setSystemPrompt(p.getSystemPrompt());
            dto.setUserTemplate(p.getUserTemplate());
            dto.setInsertable(p.getInsertable());
            aiToolService.save(userId, dto);
        }
        boolean newlyUsed = marketUseDAO.findFirstByPluginIdAndUserId(id, userId) == null;
        if (newlyUsed) {
            MarketUseDO use = new MarketUseDO();
            use.setPluginId(id);
            use.setUserId(userId);
            use.setCreateTime(LocalDateTime.now());
            marketUseDAO.save(use);
            p.setUseCount((p.getUseCount() == null ? 0 : p.getUseCount()) + 1);
            p.setUpdateTime(LocalDateTime.now());
            marketPluginDAO.save(p);
        }
        MarketUseVO vo = new MarketUseVO();
        vo.setInstalled(true);
        vo.setNewlyUsed(newlyUsed);
        vo.setToolId(p.getToolId());
        vo.setUseCount(p.getUseCount());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MarketCommentVO comment(Long userId, Long id, MarketCommentSaveDTO dto) {
        MarketPluginDO p = requirePublished(id);
        String content = dto.getContent() == null ? "" : dto.getContent().trim();
        if (content.isEmpty()) {
            throw new BizException("评论内容不能为空");
        }
        UserDO author = userDAO.findById(userId).orElse(null);
        MarketCommentDO c = new MarketCommentDO();
        c.setPluginId(id);
        c.setUserId(userId);
        c.setUserName(author == null ? "朋友" : author.getUsername());
        c.setUserAvatar(author == null ? null : author.getAvatar());
        c.setContent(content);
        c.setCreateTime(LocalDateTime.now());
        MarketCommentDO saved = marketCommentDAO.save(c);
        p.setCommentCount((p.getCommentCount() == null ? 0 : p.getCommentCount()) + 1);
        p.setUpdateTime(LocalDateTime.now());
        marketPluginDAO.save(p);
        return toCommentVO(saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Long userId, Long commentId) {
        MarketCommentDO c = marketCommentDAO.findById(commentId)
                .orElseThrow(() -> new BizException(ResultCode.NOT_FOUND, "这条评论不存在"));
        MarketPluginDO p = marketPluginDAO.findById(c.getPluginId()).orElse(null);
        boolean canDelete = c.getUserId().equals(userId) || (p != null && p.getAuthorId().equals(userId));
        if (!canDelete) {
            throw new BizException(ResultCode.FORBIDDEN, "无权删除这条评论");
        }
        marketCommentDAO.delete(c);
        if (p != null) {
            p.setCommentCount(Math.max(0, (p.getCommentCount() == null ? 0 : p.getCommentCount()) - 1));
            p.setUpdateTime(LocalDateTime.now());
            marketPluginDAO.save(p);
        }
    }

    @Override
    public List<MarketPluginVO> mine(Long userId) {
        return marketPluginDAO.findByAuthorIdOrderByCreateTimeDesc(userId).stream()
                .map(p -> toVO(p, Set.of(), Set.of()))
                .collect(Collectors.toList());
    }

    /**
     * 发布 / 更新的公共落库逻辑：解析 manifest 来源、校验、快照保存。
     */
    private MarketPluginVO save(Long userId, MarketPluginSaveDTO dto, boolean isNew) {
        Integer category = dto.getCategory();
        if (category == null || category < 0 || category > 4) {
            throw new BizException("请选择有效的插件分类");
        }
        // 解析 manifest：优先从「我的插件」按主键拷贝，否则用表单直填的字段
        String toolId;
        String name;
        String description;
        String renderType;
        String systemPrompt;
        String userTemplate;
        Boolean insertable;
        if (dto.getSourceToolId() != null) {
            AiToolDO tool = aiToolDAO.findFirstByIdAndUserId(dto.getSourceToolId(), userId);
            if (tool == null) {
                throw new BizException(ResultCode.NOT_FOUND, "没有找到要发布的插件，它可能已被删除");
            }
            toolId = tool.getToolId();
            name = tool.getName();
            description = tool.getDescription();
            renderType = tool.getRenderType();
            systemPrompt = tool.getSystemPrompt();
            userTemplate = tool.getUserTemplate();
            insertable = tool.getInsertable();
        } else {
            toolId = dto.getToolId();
            name = dto.getName();
            description = dto.getDescription();
            renderType = dto.getRenderType();
            systemPrompt = dto.getSystemPrompt();
            userTemplate = dto.getUserTemplate();
            insertable = dto.getInsertable();
        }
        String tid = toolId == null ? "" : toolId.trim();
        String nm = name == null ? "" : name.trim();
        validateManifest(tid, nm, systemPrompt);

        MarketPluginDO entity;
        if (isNew) {
            entity = new MarketPluginDO();
            entity.setAuthorId(userId);
            UserDO author = userDAO.findById(userId).orElse(null);
            entity.setAuthorName(author == null ? "朋友" : author.getUsername());
            entity.setAuthorAvatar(author == null ? null : author.getAvatar());
            entity.setStatus(1);
            entity.setLikeCount(0);
            entity.setCommentCount(0);
            entity.setUseCount(0);
            entity.setCreateTime(LocalDateTime.now());
        } else {
            entity = marketPluginDAO.findFirstByIdAndAuthorId(dto.getId(), userId);
            if (entity == null) {
                throw new BizException(ResultCode.NOT_FOUND, "没有找到你的这条发布");
            }
        }
        entity.setToolId(tid);
        entity.setName(nm);
        entity.setDescription(trimToNull(description));
        entity.setRenderType(resolveRenderType(renderType));
        entity.setSystemPrompt(systemPrompt);
        entity.setUserTemplate(trimToNull(userTemplate));
        entity.setInsertable(insertable == null ? Boolean.TRUE : insertable);
        entity.setCategory(category);
        entity.setTags(normalizeTags(dto.getTags()));
        entity.setUpdateTime(LocalDateTime.now());
        return toVO(marketPluginDAO.save(entity), Set.of(), Set.of());
    }

    private void validateManifest(String toolId, String name, String systemPrompt) {
        if (toolId.isEmpty() || !TOOL_ID_PATTERN.matcher(toolId).matches()) {
            throw new BizException("插件标识只能由字母、数字、下划线、中划线组成（1~64 位，首字符为字母或数字）");
        }
        if (name.isEmpty()) {
            throw new BizException("插件名称不能为空");
        }
        if (systemPrompt == null || systemPrompt.isBlank()) {
            throw new BizException("系统提示词不能为空：它是这个插件的角色与行为约束");
        }
    }

    private MarketPluginDO requirePublished(Long id) {
        MarketPluginDO p = marketPluginDAO.findById(id).orElse(null);
        if (p == null || p.getStatus() == null || p.getStatus() != 1) {
            throw new BizException(ResultCode.NOT_FOUND, "这个插件不存在或已下架");
        }
        return p;
    }

    private Sort sortBy(String sort) {
        String prop;
        switch (sort == null ? "" : sort) {
            case "like":
                prop = "likeCount";
                break;
            case "comment":
                prop = "commentCount";
                break;
            case "use":
                prop = "useCount";
                break;
            default:
                prop = "createTime";
        }
        return Sort.by(Sort.Direction.DESC, prop);
    }

    private List<MarketPluginVO> top(Long userId, int n, String prop) {
        Page<MarketPluginDO> page = marketPluginDAO.searchPublished("", CATEGORY_ALL,
                PageRequest.of(0, n, Sort.by(Sort.Direction.DESC, prop)));
        return toVOList(userId, page.getContent());
    }

    private List<MarketPluginVO> toVOList(Long userId, List<MarketPluginDO> items) {
        if (items.isEmpty()) {
            return List.of();
        }
        List<Long> ids = items.stream().map(MarketPluginDO::getId).collect(Collectors.toList());
        Set<Long> liked = likeIds(userId, ids);
        Set<Long> used = useIds(userId, ids);
        return items.stream().map(p -> toVO(p, liked, used)).collect(Collectors.toList());
    }

    private Set<Long> likeIds(Long userId, List<Long> pluginIds) {
        if (pluginIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(marketLikeDAO.findLikedPluginIds(userId, pluginIds));
    }

    private Set<Long> useIds(Long userId, List<Long> pluginIds) {
        if (pluginIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(marketUseDAO.findUsedPluginIds(userId, pluginIds));
    }

    private MarketPluginVO toVO(MarketPluginDO p, Set<Long> liked, Set<Long> used) {
        MarketPluginVO vo = new MarketPluginVO();
        fillCard(vo, p, liked, used);
        return vo;
    }

    private MarketPluginDetailVO toDetailVO(MarketPluginDO p, Set<Long> liked, Set<Long> used,
                                            List<MarketCommentVO> comments) {
        MarketPluginDetailVO vo = new MarketPluginDetailVO();
        fillCard(vo, p, liked, used);
        vo.setSystemPrompt(p.getSystemPrompt());
        vo.setUserTemplate(p.getUserTemplate());
        vo.setComments(comments);
        return vo;
    }

    private void fillCard(MarketPluginVO vo, MarketPluginDO p, Set<Long> liked, Set<Long> used) {
        vo.setId(p.getId());
        vo.setAuthorId(p.getAuthorId());
        vo.setAuthorName(p.getAuthorName());
        vo.setAuthorAvatar(p.getAuthorAvatar());
        vo.setToolId(p.getToolId());
        vo.setName(p.getName());
        vo.setDescription(p.getDescription());
        vo.setRenderType(p.getRenderType());
        vo.setInsertable(p.getInsertable());
        vo.setCategory(p.getCategory());
        vo.setCategoryName(categoryName(p.getCategory()));
        vo.setTags(splitTags(p.getTags()));
        vo.setLikeCount(p.getLikeCount() == null ? 0 : p.getLikeCount());
        vo.setCommentCount(p.getCommentCount() == null ? 0 : p.getCommentCount());
        vo.setUseCount(p.getUseCount() == null ? 0 : p.getUseCount());
        vo.setStatus(p.getStatus());
        vo.setLiked(liked.contains(p.getId()));
        vo.setUsed(used.contains(p.getId()));
        vo.setCreateTime(p.getCreateTime());
        vo.setUpdateTime(p.getUpdateTime());
    }

    private MarketCommentVO toCommentVO(MarketCommentDO c) {
        MarketCommentVO vo = new MarketCommentVO();
        vo.setId(c.getId());
        vo.setPluginId(c.getPluginId());
        vo.setUserId(c.getUserId());
        vo.setUserName(c.getUserName());
        vo.setUserAvatar(c.getUserAvatar());
        vo.setContent(c.getContent());
        vo.setCreateTime(c.getCreateTime());
        return vo;
    }

    private static String categoryName(Integer category) {
        if (category == null) {
            return "通用";
        }
        switch (category) {
            case 1:
                return "科研";
            case 2:
                return "生活";
            case 3:
                return "小说";
            case 4:
                return "随笔";
            default:
                return "通用";
        }
    }

    private static List<String> splitTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tags.split("[,，]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .limit(8)
                .collect(Collectors.toList());
    }

    private static String normalizeTags(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.replace('，', ',').trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private static String resolveRenderType(String raw) {
        return raw == null || raw.isBlank() ? "text" : raw.trim();
    }

    private static String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
