-- =====================================================================
-- 小窝笔记（MyNote）私有知识管理平台 —— 建表脚本
-- =====================================================================

CREATE DATABASE IF NOT EXISTS `newnote` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE `newnote`;

-- 便于重复执行：先按依赖倒序清理
DROP TABLE IF EXISTS `mn_note_reference`;
DROP TABLE IF EXISTS `mn_note_tag`;
DROP TABLE IF EXISTS `mn_tag`;
DROP TABLE IF EXISTS `mn_diagram`;
DROP TABLE IF EXISTS `mn_note_history`;
DROP TABLE IF EXISTS `mn_ai_message`;
DROP TABLE IF EXISTS `mn_ai_session`;
DROP TABLE IF EXISTS `mn_ai_config`;
DROP TABLE IF EXISTS `mn_ai_tool`;
DROP TABLE IF EXISTS `mn_writing_log`;
DROP TABLE IF EXISTS `mn_reference`;
DROP TABLE IF EXISTS `mn_knowledge_card`;
DROP TABLE IF EXISTS `mn_note`;
DROP TABLE IF EXISTS `mn_folder`;
DROP TABLE IF EXISTS `mn_user`;

-- ---------------------------------------------------------------------
-- 1. 用户表：账号一旦注册不可修改，昵称/头像/主题等可改
-- ---------------------------------------------------------------------
CREATE TABLE `mn_user`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `account`     VARCHAR(50)     NOT NULL COMMENT '登录账号，注册后不可修改',
    `username`    VARCHAR(50)     NOT NULL COMMENT '昵称（可修改）',
    `password`    VARCHAR(100)    NOT NULL COMMENT '登录密码，BCrypt 密文存储',
    `email`       VARCHAR(100)             DEFAULT NULL COMMENT '邮箱，用于找回密码',
    `avatar`      VARCHAR(255)             DEFAULT NULL COMMENT '头像相对路径，NULL 时前端用首字母占位',
    `intro`       VARCHAR(255)             DEFAULT NULL COMMENT '一句话签名',
    `theme_color` VARCHAR(20)              DEFAULT '#C06A46' COMMENT '主题主色（用户自定义）',
    `theme_mode`  VARCHAR(20)              DEFAULT 'light' COMMENT '主题模式：light/dark',
    `note_bg`     VARCHAR(255)             DEFAULT 'paper' COMMENT '笔记书页背景：paper/grid/dot/cloud/自定义图片路径',
    `weather_city` VARCHAR(50)             DEFAULT '杭州' COMMENT '主页天气所在城市',
    `daily_goal`  INT UNSIGNED    NOT NULL DEFAULT 1000 COMMENT '每日字数目标',
    `font_size`   INT UNSIGNED    NOT NULL DEFAULT 16 COMMENT '编辑器字号（px）',
    `ai_feature_map` TEXT                   DEFAULT NULL COMMENT '笔记类型×AI功能开关的 JSON 配置',
    `status`      TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1正常 0禁用',
    `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    `update_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_account` (`account`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  ROW_FORMAT = DYNAMIC COMMENT ='用户表';

-- ---------------------------------------------------------------------
-- 2. 目录表：自关联树，parent_id = 0 表示根目录
-- ---------------------------------------------------------------------
CREATE TABLE `mn_folder`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '归属用户',
    `parent_id`   BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '父目录，0 为根',
    `name`        VARCHAR(100)    NOT NULL COMMENT '目录名',
    `icon`        VARCHAR(50)              DEFAULT NULL COMMENT '目录图标标识',
    `sort_order`  INT             NOT NULL DEFAULT 0 COMMENT '同级排序，越小越靠前',
    `is_deleted`  TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：1已删除',
    `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_parent` (`user_id`, `parent_id`),
    KEY `idx_user_sort` (`user_id`, `sort_order`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  ROW_FORMAT = DYNAMIC COMMENT ='笔记目录表（树状结构）';

-- ---------------------------------------------------------------------
-- 3. 笔记表：正文直接存 Markdown 源文本
--    note_type 决定 AI 助手的行为分支（科研 / 生活 / 小说 / 随笔）
-- ---------------------------------------------------------------------
CREATE TABLE `mn_note`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '归属用户',
    `folder_id`   BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '所属目录，0 表示根下未分类',
    `title`       VARCHAR(200)    NOT NULL COMMENT '笔记标题',
    `content`     LONGTEXT COMMENT '笔记正文（Markdown 源文本）',
    `summary`     VARCHAR(500)             DEFAULT NULL COMMENT '摘要，供 AI 组装同目录上下文使用',
    `note_type`   TINYINT UNSIGNED NOT NULL DEFAULT 4 COMMENT '类型：1科研 2生活 3小说 4随笔',
    `word_count`  INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '字数（中文按字、英文按词统计）',
    `mood`        VARCHAR(20)              DEFAULT NULL COMMENT '创作情绪标签，主页开场白使用',
    `is_top`      TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否置顶：1置顶',
    `is_deleted`  TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：1在回收站',
    `delete_time` DATETIME                 DEFAULT NULL COMMENT '进入回收站时间',
    `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_folder` (`user_id`, `folder_id`, `is_deleted`),
    KEY `idx_user_update` (`user_id`, `update_time`),
    KEY `idx_user_type` (`user_id`, `note_type`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  ROW_FORMAT = DYNAMIC COMMENT ='笔记表';

-- ---------------------------------------------------------------------
-- 4. 标签表 + 笔记标签关联表：全局标签池，多对多
-- ---------------------------------------------------------------------
CREATE TABLE `mn_tag`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '归属用户',
    `name`        VARCHAR(50)     NOT NULL COMMENT '标签名',
    `color`       VARCHAR(20)     NOT NULL DEFAULT '#E3A878' COMMENT '标签色',
    `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_name` (`user_id`, `name`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  ROW_FORMAT = DYNAMIC COMMENT ='标签表';

CREATE TABLE `mn_note_tag`
(
    `id`     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `note_id` BIGINT UNSIGNED NOT NULL COMMENT '笔记',
    `tag_id`  BIGINT UNSIGNED NOT NULL COMMENT '标签',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_note_tag` (`note_id`, `tag_id`),
    KEY `idx_tag` (`tag_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  ROW_FORMAT = DYNAMIC COMMENT ='笔记与标签关联表';

-- ---------------------------------------------------------------------
-- 5. 书写日志：主页字数看板、连续打卡、热力图的数据源
--    同一天同一笔记多次保存，增量累加到当日记录上
-- ---------------------------------------------------------------------
CREATE TABLE `mn_writing_log`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '归属用户',
    `note_id`     BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '触发记录的笔记',
    `log_date`    DATE            NOT NULL COMMENT '记录日期',
    `word_delta`  INT             NOT NULL DEFAULT 0 COMMENT '当日新增字数（可为负，表示删改）',
    `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_date` (`user_id`, `log_date`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  ROW_FORMAT = DYNAMIC COMMENT ='书写日志表（激励看板数据源）';

-- ---------------------------------------------------------------------
-- 6. 背景知识库：小说/生活类创作的"时代常识"飘窗数据源
--    keywords 以逗号分隔，后端按当前笔记文本做关键词命中
-- ---------------------------------------------------------------------
CREATE TABLE `mn_knowledge_card`
(
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `category`   VARCHAR(30)     NOT NULL COMMENT '分类：历史背景/生活常识/写作技法/世界观设定',
    `era`        VARCHAR(50)              DEFAULT NULL COMMENT '时代或朝代',
    `region`     VARCHAR(50)              DEFAULT NULL COMMENT '地域',
    `title`      VARCHAR(200)    NOT NULL COMMENT '知识标题',
    `content`    TEXT            NOT NULL COMMENT '知识正文（可直接插入笔记）',
    `keywords`   VARCHAR(500)    NOT NULL COMMENT '命中关键词，逗号分隔',
    `source`     VARCHAR(200)             DEFAULT NULL COMMENT '出处，供用户查证',
    `ref_count`  INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '被作者采纳次数',
    `create_time` DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_category` (`category`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  ROW_FORMAT = DYNAMIC COMMENT ='创作背景知识库';

-- ---------------------------------------------------------------------
-- 7. 参考文献库：科研类笔记的文献候选池（本地部分）
--    联网可用时后端会再叠加 OpenAlex/Crossref 检索结果
-- ---------------------------------------------------------------------
CREATE TABLE `mn_reference`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `title`       VARCHAR(500)    NOT NULL COMMENT '文献标题',
    `authors`     VARCHAR(500)             DEFAULT NULL COMMENT '作者，逗号分隔',
    `journal`     VARCHAR(300)             DEFAULT NULL COMMENT '期刊/会议',
    `pub_year`    SMALLINT                 DEFAULT NULL COMMENT '发表年份',
    `doi`         VARCHAR(200)             DEFAULT NULL COMMENT 'DOI',
    `url`         VARCHAR(500)             DEFAULT NULL COMMENT '原文链接',
    `field`       VARCHAR(100)             DEFAULT NULL COMMENT '学科方向',
    `keywords`    VARCHAR(500)             DEFAULT NULL COMMENT '关键词，逗号分隔',
    `abstract`    TEXT                     DEFAULT NULL COMMENT '摘要',
    `cited_count` INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '被引次数（联网检索时回填）',
    `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_field` (`field`),
    KEY `idx_year` (`pub_year`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  ROW_FORMAT = DYNAMIC COMMENT ='参考文献表';

CREATE TABLE `mn_note_reference`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `note_id`     BIGINT UNSIGNED NOT NULL COMMENT '笔记',
    `ref_id`      BIGINT UNSIGNED NOT NULL COMMENT '文献',
    `source`      VARCHAR(20)     NOT NULL DEFAULT 'ai' COMMENT '来源：ai智能推荐 / manual手动添加',
    `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关联时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_note_ref` (`note_id`, `ref_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  ROW_FORMAT = DYNAMIC COMMENT ='笔记与参考文献关联表';

-- ---------------------------------------------------------------------
-- 8. AI 配置表：一个用户可保存多个模型，api_key 使用 AES 加密后落库
-- ---------------------------------------------------------------------
CREATE TABLE `mn_ai_config`
(
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`       BIGINT UNSIGNED NOT NULL COMMENT '归属用户',
    `provider_code` VARCHAR(50)     NOT NULL COMMENT '服务商标识：deepseek/openai/qianwen/kimi/zhipu/custom',
    `provider_name` VARCHAR(50)     NOT NULL COMMENT '服务商展示名',
    `base_url`      VARCHAR(200)    NOT NULL COMMENT 'OpenAI 兼容接口地址',
    `model_name`    VARCHAR(100)    NOT NULL COMMENT '模型名，如 deepseek-chat',
    `api_key`       VARCHAR(500)             DEFAULT NULL COMMENT 'API Key（AES 密文），NULL 表示未配置',
    `temperature`   DECIMAL(3, 2)   NOT NULL DEFAULT 0.70 COMMENT '采样温度',
    `max_tokens`    INT UNSIGNED    NOT NULL DEFAULT 2048 COMMENT '最大输出 token',
    `is_default`    TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否为当前使用模型：1是',
    `is_enabled`    TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '是否启用：0停用',
    `create_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_model` (`user_id`, `provider_code`, `model_name`),
    KEY `idx_user_default` (`user_id`, `is_default`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  ROW_FORMAT = DYNAMIC COMMENT ='AI 模型配置表';

-- ---------------------------------------------------------------------
-- 9. AI 会话与消息：对话历史可回溯，笔记删除时会话保留
-- ---------------------------------------------------------------------
CREATE TABLE `mn_ai_session`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '归属用户',
    `note_id`     BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '绑定的笔记，0 为自由对话',
    `title`       VARCHAR(200)             DEFAULT NULL COMMENT '会话标题，取首问前 20 字',
    `message_count` INT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '消息条数',
    `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后消息时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_note` (`user_id`, `note_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  ROW_FORMAT = DYNAMIC COMMENT ='AI 会话表';

CREATE TABLE `mn_ai_message`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `session_id`  BIGINT UNSIGNED NOT NULL COMMENT '所属会话',
    `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '归属用户（冗余，便于统计与鉴权）',
    `role`        VARCHAR(20)     NOT NULL COMMENT '角色：user/assistant/system',
    `content`     TEXT            NOT NULL COMMENT '消息内容',
    `scene`       VARCHAR(30)              DEFAULT NULL COMMENT '场景：chat/polish/expand/knowledge/reference',
    `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_session` (`session_id`, `id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  ROW_FORMAT = DYNAMIC COMMENT ='AI 消息表';

-- ---------------------------------------------------------------------
-- 10. 逻辑图：多篇笔记生成的 Mermaid 图，可保存回看
-- ---------------------------------------------------------------------
CREATE TABLE `mn_diagram`
(
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`      BIGINT UNSIGNED NOT NULL COMMENT '归属用户',
    `title`        VARCHAR(200)    NOT NULL COMMENT '图标题',
    `diagram_type` VARCHAR(30)     NOT NULL DEFAULT 'mindmap' COMMENT '图类型：mindmap/flowchart/graph/timeline',
    `note_ids`     VARCHAR(500)             DEFAULT NULL COMMENT '参与生成的笔记 id，逗号分隔',
    `code`         TEXT            NOT NULL COMMENT 'Mermaid 源码',
    `create_time`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`, `create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  ROW_FORMAT = DYNAMIC COMMENT ='笔记逻辑图表（Mermaid）';

-- ---------------------------------------------------------------------
-- 11. 笔记历史版本：AI 覆盖正文前先存一份，支持一键回退
-- ---------------------------------------------------------------------
CREATE TABLE `mn_note_history`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `note_id`     BIGINT UNSIGNED NOT NULL COMMENT '笔记',
    `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '归属用户',
    `title`       VARCHAR(200)             DEFAULT NULL COMMENT '当时的标题',
    `content`     LONGTEXT COMMENT '当时正文快照',
    `word_count`  INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '当时字数',
    `source`      VARCHAR(20)     NOT NULL DEFAULT 'save' COMMENT '快照来源：save手动保存 / ai AI覆盖前 / import导入',
    `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '快照时间',
    PRIMARY KEY (`id`),
    KEY `idx_note_time` (`note_id`, `create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  ROW_FORMAT = DYNAMIC COMMENT ='笔记历史版本表';

-- ---------------------------------------------------------------------
-- 12. 用户自定义 AI 工具插件表：声明式插件按账号隔离存储
--     tool_id 在账号内唯一；执行时后端组装上下文并调用大模型
-- ---------------------------------------------------------------------
CREATE TABLE `mn_ai_tool`
(
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`       BIGINT UNSIGNED NOT NULL COMMENT '归属用户',
    `tool_id`       VARCHAR(100)    NOT NULL COMMENT '插件标识（账号内唯一，对外展示为 user:toolId）',
    `name`          VARCHAR(100)    NOT NULL COMMENT '插件展示名',
    `description`   VARCHAR(500)             DEFAULT NULL COMMENT '一句话说明',
    `render_type`   VARCHAR(30)     NOT NULL DEFAULT 'text' COMMENT '渲染类型：text/cards/references',
    `system_prompt` TEXT            COMMENT '系统提示词',
    `user_template` TEXT            COMMENT '用户模板，支持 {{note}} {{selection}} {{siblings}} {{title}}',
    `is_insertable` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '结果是否可插入正文：1是',
    `is_enabled`    TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '是否启用：0停用',
    `sort_order`    INT             NOT NULL DEFAULT 0 COMMENT '排序号，越小越靠前',
    `create_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_tool` (`user_id`, `tool_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  ROW_FORMAT = DYNAMIC COMMENT ='用户自定义 AI 工具插件表';
