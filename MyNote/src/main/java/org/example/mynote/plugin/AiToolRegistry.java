package org.example.mynote.plugin;

import org.example.mynote.ai.NoteContextBuilder;
import org.example.mynote.ai.OpenAiClient;
import org.example.mynote.common.BizException;
import org.example.mynote.common.ResultCode;
import org.example.mynote.config.AppProperties;
import org.example.mynote.dao.AiToolDAO;
import org.example.mynote.dao.NoteDAO;
import org.example.mynote.dto.AiToolRunDTO;
import org.example.mynote.entity.AiToolDO;
import org.example.mynote.service.AiConfigService;
import org.example.mynote.vo.AiToolRunVO;
import org.example.mynote.vo.AiToolVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 工具插件注册表：内置插件与用户插件统一入口。
 *
 * <p>内置插件以 Spring Bean 形式注入（实现 {@link AiToolPlugin} 即自动被发现），
 * 用户插件按账号从 {@link AiToolDAO} 读取并动态包装成 {@link DeclarativeToolPlugin}。
 * 面板清单是两者的合并视图；执行按有效 id 路由到具体实现。</p>
 *
 * @author MyNote
 */
@Component
public class AiToolRegistry {

    /**
     * 用户插件有效 id 前缀，用于与内置短名区分
     */
    public static final String USER_PREFIX = "user:";

    private final Map<String, AiToolPlugin> builtins;

    private final AiToolDAO aiToolDAO;

    private final NoteDAO noteDAO;

    private final OpenAiClient openAiClient;

    private final AiConfigService aiConfigService;

    private final NoteContextBuilder noteContextBuilder;

    private final AppProperties appProperties;

    public AiToolRegistry(List<AiToolPlugin> builtins, AiToolDAO aiToolDAO, NoteDAO noteDAO,
                          OpenAiClient openAiClient, AiConfigService aiConfigService,
                          NoteContextBuilder noteContextBuilder, AppProperties appProperties) {
        this.aiToolDAO = aiToolDAO;
        this.noteDAO = noteDAO;
        this.openAiClient = openAiClient;
        this.aiConfigService = aiConfigService;
        this.noteContextBuilder = noteContextBuilder;
        this.appProperties = appProperties;
        this.builtins = new LinkedHashMap<>();
        for (AiToolPlugin plugin : builtins) {
            if (this.builtins.putIfAbsent(plugin.id(), plugin) != null) {
                throw new IllegalStateException("内置插件 id 重复: " + plugin.id());
            }
        }
    }

    /**
     * 当前用户可用的插件清单：内置 + 已启用的用户插件。
     *
     * @param userId 用户 id
     * @return 清单
     */
    public List<AiToolVO> listActive(Long userId) {
        List<AiToolVO> result = new ArrayList<>();
        for (AiToolPlugin plugin : builtins.values()) {
            result.add(toVO(plugin.id(), "builtin", plugin.name(), plugin.description(),
                    plugin.renderType(), plugin.requiresModel(), plugin.insertable()));
        }
        for (AiToolDO tool : aiToolDAO.findByUserIdOrderBySortOrderAscIdAsc(userId)) {
            if (!Boolean.TRUE.equals(tool.getEnabled())) {
                continue;
            }
            result.add(toUserVO(tool));
        }
        return result;
    }

    /**
     * 按有效 id 执行插件。
     *
     * @param userId 用户 id
     * @param dto    入参
     * @return 结果
     */
    public AiToolRunVO run(Long userId, AiToolRunDTO dto) {
        String id = dto.getId();
        AiToolPlugin plugin;
        if (id.startsWith(USER_PREFIX)) {
            String toolId = id.substring(USER_PREFIX.length());
            AiToolDO tool = aiToolDAO.findFirstByUserIdAndToolId(userId, toolId);
            if (tool == null) {
                throw new BizException(ResultCode.NOT_FOUND, "找不到这个插件，可能已被删除");
            }
            if (!Boolean.TRUE.equals(tool.getEnabled())) {
                throw new BizException(ResultCode.BIZ_LIMIT, "该插件已停用，请到「我的插件」里启用");
            }
            plugin = new DeclarativeToolPlugin(tool);
        } else {
            plugin = builtins.get(id);
            if (plugin == null) {
                throw new BizException(ResultCode.NOT_FOUND, "找不到这个插件");
            }
        }
        AiToolContext ctx = new AiToolContext(userId, noteDAO, openAiClient, aiConfigService,
                noteContextBuilder, appProperties);
        AiToolRequest request = new AiToolRequest(dto.getNoteId(), dto.getSelection(), dto.getSessionId(),
                dto.getParams());
        AiToolResult result = plugin.run(ctx, request);
        AiToolRunVO vo = new AiToolRunVO();
        vo.setToolId(id);
        vo.setRenderType(result.getRenderType());
        vo.setData(result.getData());
        return vo;
    }

    private AiToolVO toUserVO(AiToolDO tool) {
        return toVO(USER_PREFIX + tool.getToolId(), "user", tool.getName(), tool.getDescription(),
                tool.getRenderType(), true, tool.getInsertable());
    }

    private static AiToolVO toVO(String id, String origin, String name, String description,
                                 String renderType, boolean requiresModel, Boolean insertable) {
        AiToolVO vo = new AiToolVO();
        vo.setId(id);
        vo.setOrigin(origin);
        vo.setName(name);
        vo.setDescription(description == null ? "" : description);
        vo.setRenderType(renderType == null ? "text" : renderType);
        vo.setRequiresModel(requiresModel);
        vo.setInsertable(insertable == null ? false : insertable);
        return vo;
    }
}
