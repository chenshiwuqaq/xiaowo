-- =====================================================================
-- 增量迁移：插件市场（mn_market_*）
-- 适用：已有数据库可直接执行本文件；全新安装走 01_schema.sql 后仍需执行本文件。
-- 用途：把用户自定义的 AI 插件发布到市场，供他人浏览、点赞、评论、下载使用。
-- 设计说明：
--   * mn_market_plugin 是「市场条目」——发布时对 manifest 做一次快照，与作者原插件解耦，
--     作者后续改自己的插件不会影响市场里的这份。
--   * like_count / comment_count / use_count 为反规范化计数，用于排行榜快速排序；
--     明细在 mn_market_like / mn_market_comment / mn_market_use，两者在同一事务内维护。
--   * category 与笔记类型对齐：0 通用 / 1 科研 / 2 生活 / 3 小说 / 4 随笔。
-- =====================================================================

USE `newnote`;

-- ---------------------------------------------------------------------
-- 1. 市场插件条目（上架清单）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `mn_market_plugin`
(
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `author_id`     BIGINT UNSIGNED NOT NULL COMMENT '发布者用户 id',
    `author_name`   VARCHAR(50)     NOT NULL COMMENT '发布者昵称（发布时快照）',
    `author_avatar` VARCHAR(255)             DEFAULT NULL COMMENT '发布者头像（发布时快照）',
    `tool_id`       VARCHAR(100)    NOT NULL COMMENT '原插件标识（展示用，市场内不唯一）',
    `name`          VARCHAR(100)    NOT NULL COMMENT '插件名称',
    `description`   VARCHAR(500)             DEFAULT NULL COMMENT '一句话说明',
    `render_type`   VARCHAR(30)     NOT NULL DEFAULT 'text' COMMENT '渲染类型：text/cards/references',
    `system_prompt` TEXT            COMMENT '系统提示词（manifest 快照）',
    `user_template` TEXT            COMMENT '用户模板（manifest 快照）',
    `is_insertable` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '结果是否可插入正文：1是',
    `category`      TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '分类：0通用 1科研 2生活 3小说 4随笔',
    `tags`          VARCHAR(200)             DEFAULT NULL COMMENT '自由标签，逗号分隔',
    `status`        TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1上架 0下架',
    `like_count`    INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '点赞数（反规范化）',
    `comment_count` INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '评论数（反规范化）',
    `use_count`     INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '使用/下载数（反规范化）',
    `create_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
    `update_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_status_like` (`status`, `like_count`),
    KEY `idx_status_comment` (`status`, `comment_count`),
    KEY `idx_status_use` (`status`, `use_count`),
    KEY `idx_status_time` (`status`, `create_time`),
    KEY `idx_author` (`author_id`),
    KEY `idx_status_category` (`status`, `category`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  ROW_FORMAT = DYNAMIC COMMENT ='插件市场条目表';

-- ---------------------------------------------------------------------
-- 2. 市场点赞明细（去重：一个用户对一条目最多一次）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `mn_market_like`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `plugin_id`   BIGINT UNSIGNED NOT NULL COMMENT '市场条目',
    `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '点赞用户',
    `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plugin_user` (`plugin_id`, `user_id`),
    KEY `idx_user` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  ROW_FORMAT = DYNAMIC COMMENT ='插件市场点赞明细表';

-- ---------------------------------------------------------------------
-- 3. 市场评论（昵称/头像冗余快照，避免每次列表都 join 用户表）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `mn_market_comment`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `plugin_id`   BIGINT UNSIGNED NOT NULL COMMENT '市场条目',
    `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '评论用户',
    `user_name`   VARCHAR(50)     NOT NULL COMMENT '评论者昵称（快照）',
    `user_avatar` VARCHAR(255)             DEFAULT NULL COMMENT '评论者头像（快照）',
    `content`     VARCHAR(1000)   NOT NULL COMMENT '评论内容',
    `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评论时间',
    PRIMARY KEY (`id`),
    KEY `idx_plugin` (`plugin_id`, `create_time`),
    KEY `idx_user` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  ROW_FORMAT = DYNAMIC COMMENT ='插件市场评论表';

-- ---------------------------------------------------------------------
-- 4. 市场使用/下载明细（去重：一个用户对一条目最多计一次使用）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `mn_market_use`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `plugin_id`   BIGINT UNSIGNED NOT NULL COMMENT '市场条目',
    `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '使用用户',
    `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '使用时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plugin_user` (`plugin_id`, `user_id`),
    KEY `idx_user` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  ROW_FORMAT = DYNAMIC COMMENT ='插件市场使用记录表';
