-- =====================================================================
-- 增量迁移：用户自定义 AI 工具插件表（mn_ai_tool）
-- 适用：已有数据库可直接执行本文件；全新安装走 01_schema.sql（已含本表）。
-- 用途：声明式插件按账号隔离存储，用户只填 manifest 即可扩展 AI 助手。
-- =====================================================================

USE `newnote`;

CREATE TABLE IF NOT EXISTS `mn_ai_tool`
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
