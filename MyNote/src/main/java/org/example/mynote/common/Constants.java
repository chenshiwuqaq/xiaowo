package org.example.mynote.common;

/**
 * 全局常量。
 *
 * <p>按阿里规约要求，未在作用域内使用一次的"魔法值"统一在此定义。</p>
 *
 * @author MyNote
 */
public final class Constants {

    /**
     * 登录令牌请求头名
     */
    public static final String HEADER_TOKEN = "Authorization";

    /**
     * 令牌前缀
     */
    public static final String TOKEN_PREFIX = "Bearer ";

    /**
     * 请求属性中保存当前用户 id 的键
     */
    public static final String ATTR_USER_ID = "mynoteUserId";

    /**
     * 根目录的 parent_id 约定值
     */
    public static final Long ROOT_FOLDER_ID = 0L;

    /**
     * 未分目录的笔记 folder_id 约定值
     */
    public static final Long UNFILED_FOLDER_ID = 0L;

    /**
     * 笔记状态：正常
     */
    public static final int NOTE_STATUS_NORMAL = 0;

    /**
     * 笔记状态：回收站
     */
    public static final int NOTE_STATUS_DELETED = 1;

    /**
     * 笔记类型：科研
     */
    public static final int NOTE_TYPE_RESEARCH = 1;

    /**
     * 笔记类型：生活
     */
    public static final int NOTE_TYPE_LIFE = 2;

    /**
     * 笔记类型：小说
     */
    public static final int NOTE_TYPE_FICTION = 3;

    /**
     * 笔记类型：随笔
     */
    public static final int NOTE_TYPE_ESSAY = 4;

    /**
     * AI 场景：普通对话
     */
    public static final String SCENE_CHAT = "chat";

    /**
     * AI 场景：润色
     */
    public static final String SCENE_POLISH = "polish";

    /**
     * AI 场景：背景知识
     */
    public static final String SCENE_KNOWLEDGE = "knowledge";

    /**
     * AI 场景：参考文献
     */
    public static final String SCENE_REFERENCE = "reference";

    /**
     * 消息角色：用户
     */
    public static final String ROLE_USER = "user";

    /**
     * 消息角色：助手
     */
    public static final String ROLE_ASSISTANT = "assistant";

    /**
     * 消息角色：系统
     */
    public static final String ROLE_SYSTEM = "system";

    /**
     * 历史快照来源：手动保存
     */
    public static final String HISTORY_SOURCE_SAVE = "save";

    /**
     * 历史快照来源：AI 覆盖前
     */
    public static final String HISTORY_SOURCE_AI = "ai";

    /**
     * 历史快照来源：导入
     */
    public static final String HISTORY_SOURCE_IMPORT = "import";

    private Constants() {
    }
}
