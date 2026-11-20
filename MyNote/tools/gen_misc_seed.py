#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
生成 05_seed_misc.sql —— AI 配置 / 逻辑图 / 会话 / 书写日志示例数据。

书写日志按"最近 70 天"生成，并刻意留出一段断档，
这样主页的连续打卡、热力图、目标进度在演示时能看到真实的三种状态：
连续达成、当日未达、历史断档。
"""
import datetime
import io
import random

TODAY = datetime.date(2026, 10, 4)
DAYS = 70

# (距今天数, 当日字数, 关联笔记)  —— 负值表示当天没写
PLAN_USER1 = [
    (0, 620, 5), (1, 1480, 6), (2, 1120, 12), (3, 96, 3), (4, 1650, 5),
    (5, 1210, 1), (6, 0, None), (7, 0, None), (8, 0, None),
    (9, 830, 8), (10, 1420, 5), (11, 1560, 6), (12, 740, 11), (13, 1180, 1),
    (14, 0, None), (15, 2100, 4), (16, 1740, 1), (17, 620, 2), (18, 980, 3),
    (19, 0, None), (20, 0, None), (21, 1310, 5), (22, 1620, 6), (23, 1890, 5),
    (24, 430, 9), (25, 1240, 12), (26, 1510, 7), (27, 1780, 5), (28, 60, 13),
    (29, 0, None), (30, 1120, 1), (31, 1350, 2), (32, 1660, 4), (33, 1980, 5),
    (34, 870, 8), (35, 1240, 10), (36, 1420, 12), (37, 0, None),
    (38, 760, 9), (39, 1180, 1), (40, 1530, 5), (41, 1710, 6), (42, 690, 11),
    (43, 1020, 7), (44, 1280, 8), (45, 0, None), (46, 540, 10), (47, 1160, 12),
    (48, 1440, 5), (49, 1690, 6), (50, 810, 1), (51, 1190, 2), (52, 1460, 4),
    (53, 0, None), (54, 0, None), (55, 720, 10), (56, 1080, 8), (57, 1360, 9),
    (58, 1610, 5), (59, 940, 6), (60, 1220, 12), (61, 0, None),
    (62, 680, 10), (63, 1140, 11), (64, 1410, 7), (65, 1650, 8), (66, 900, 9),
    (67, 1180, 5), (68, 1330, 6), (69, 470, 10),
]

PLAN_USER2 = [(d, random.Random(d * 7 + 3).randint(0, 1100), 14) for d in range(0, 40)]


def q(v):
    return "NULL" if v is None else "'%s'" % str(v).replace("'", "''")


def main():
    random.seed(20261004)
    lines = []
    lines.append("""-- =====================================================================
-- 小窝笔记（MyNote）—— 示例数据：AI 配置 / 逻辑图 / 会话 / 书写日志
-- 本文件由 tools/gen_misc_seed.py 生成，书写日志覆盖最近 %d 天并留有断档。
-- =====================================================================

USE `newnote`;

-- ---------------------------------------------------------------------
-- AI 模型配置：api_key 一律留空，由用户在"个人设置 → AI 模型"中填入，
-- 后端以 AES-256 加密后写库，接口回显为掩码。
-- ---------------------------------------------------------------------
INSERT INTO `mn_ai_config` (`user_id`, `provider_code`, `provider_name`, `base_url`, `model_name`, `api_key`,
                            `temperature`, `max_tokens`, `is_default`, `is_enabled`)
VALUES (1, 'deepseek', 'DeepSeek', 'https://api.deepseek.com/v1', 'deepseek-chat', NULL, 0.70, 2048, 1, 1),
       (1, 'openai', 'OpenAI', 'https://api.openai.com/v1', 'gpt-4o-mini', NULL, 0.70, 2048, 0, 1),
       (1, 'custom', '本地/自定义（Ollama 等）', 'http://127.0.0.1:11434/v1', 'qwen2.5:7b', NULL, 0.80, 2048, 0, 0),
       (2, 'qianwen', '通义千问', 'https://dashscope.aliyuncs.com/compatible-mode/v1', 'qwen-plus', NULL, 0.70, 2048,
        1, 1),
       (3, 'kimi', 'Kimi', 'https://api.moonshot.cn/v1', 'moonshot-v1-8k', NULL, 0.90, 2048, 1, 1);

-- ---------------------------------------------------------------------
-- 已保存的逻辑图（Mermaid 源码，前端 mermaid 渲染，可再编辑）
-- ---------------------------------------------------------------------
INSERT INTO `mn_diagram` (`user_id`, `title`, `diagram_type`, `note_ids`, `code`)
VALUES (1, '长安幻夜 · 结构与线索', 'mindmap', '5,6,7,8',
 'mindmap
  root((长安幻夜))
    制度背景
      坊市与围墙
      宵禁与弛禁
      东市西市
    人物
      裴元昭
        千牛备身
        记账式推理
        父亲遗言
      娜尔
        粟特银盘
        行话与身份
    线索
      鹿纹数目
      半册账目
      上元夜灯轮
    主题
      秩序与缝隙
      数字作为证据'),
 (1, 'GNN 技术演进逻辑', 'flowchart', '1,2,3,4',
 'flowchart LR
    A[谱方法<br/>图傅里叶变换] --> B[GCN 2017<br/>一阶切比雪夫近似]
    B --> C{静态图?}
    C -->|否| D[GraphSAGE 2017<br/>采样+聚合 归纳式]
    C -->|权重固定| E[GAT 2018<br/>注意力加权]
    D --> F[过平滑<br/>深度受限]
    E --> F
    F --> G[残差与传播解耦<br/>APPNP / GCNII]
    B --> H[表达能力瓶颈<br/>GIN 2019]
    H --> I[动态异构图<br/>本课题切入点]
    G --> I
    I --> J[创新点 C3<br/>结构-语义联合对比学习]'),
 (1, '科研笔记阅读路径', 'graph', '1,2,3,4,14',
 'graph TD
    S0[入门: 综述 1901.00596] --> S1[GCN]
    S1 --> S2[GraphSAGE 归纳式]
    S2 --> S3[GAT 注意力]
    S3 --> S4[GIN 表达能力]
    S4 --> S5[PNA 聚合器族]
    S1 --> S6[过平滑]
    S6 --> S7[APPNP / AGP-GNN]
    S2 --> S8[动态图 TGN]
    S8 --> S9[课题切入点]
    S5 --> S9');

-- ---------------------------------------------------------------------
-- 笔记与参考文献的既有绑定（科研笔记打开即可看到已归档文献）
-- 用标题匹配取 ref_id，避免依赖自增主键的具体数值。
-- ---------------------------------------------------------------------
"""
    )
    binds = [
        (1, "Semi-Supervised Classification with Graph Convolutional Networks", "manual"),
        (1, "Inductive Representation Learning on Large Graphs", "manual"),
        (1, "Graph Attention Networks", "manual"),
        (1, "How Powerful are Graph Neural Networks?", "ai"),
        (1, "A Comprehensive Survey on Graph Neural Networks", "manual"),
        (2, "Inductive Representation Learning on Large Graphs", "manual"),
        (2, "The Graph Neural Network Model", "ai"),
        (3, "Deep Residual Learning for Image Recognition", "ai"),
        (3, "Decoupled Weight Decay Regularization", "ai"),
        (4, "Principal Neighbourhood Aggregation for Graph Nets", "ai"),
        (4, "Temporal Graph Networks for Deep Learning on Dynamic Graphs", "ai"),
        (14, "Temporal Graph Networks for Deep Learning on Dynamic Graphs", "manual"),
        (14, "Neural Graph Collaborative Filtering", "ai"),
        (12, "Flow: The Psychology of Optimal Experience", "manual"),
    ]
    body = []
    for note_id, title, source in binds:
        body.append("INSERT INTO `mn_note_reference` (`note_id`, `ref_id`, `source`)\n"
                    "SELECT %d, id, '%s' FROM `mn_reference` WHERE `title` = %s;" % (
                        note_id, source, q(title)))
    lines.append("\n".join(body))

    lines.append("""

-- ---------------------------------------------------------------------
-- AI 会话示例（打开助手面板时不至于空白）
-- ---------------------------------------------------------------------
INSERT INTO `mn_ai_session` (`id`, `user_id`, `note_id`, `title`, `message_count`, `create_time`, `update_time`)
VALUES (1, 1, 5, '开场鼓声太软，换更有压迫感的写法', 2, '2026-10-02 22:31:00', '2026-10-02 22:33:00');

INSERT INTO `mn_ai_message` (`session_id`, `user_id`, `role`, `content`, `scene`, `create_time`)
VALUES (1, 1, 'user', '第一段鼓声的比喻太软了，能不能换成更有压迫感的？', 'chat', '2026-10-02 22:31:00'),
       (1, 1, 'assistant',
        '把「淌」换成带强制意味的动词。建议：「鼓声从承天门压下来，顺着朱雀大街一路碾过去，两侧一百零八坊是被砸醒的。」'
        '同时删掉后文「一个个摇醒」，同一个意象不必解释两遍。要我按这个方向出三版吗？', 'chat',
        '2026-10-02 22:33:00');

-- ---------------------------------------------------------------------
-- 书写日志（主页字数看板 / 连续打卡 / 热力图数据源）
-- ---------------------------------------------------------------------
""")

    vals = []
    for offset, words, note_id in PLAN_USER1:
        d = TODAY - datetime.timedelta(days=offset)
        if words <= 0:
            continue
        hour = random.choice([7, 8, 9, 14, 20, 21, 22, 23, 23])
        vals.append("       (1, %d, '%s', %d, '%s %02d:%02d:00')" % (
            note_id or 0, d.isoformat(), words, d.isoformat(), hour, random.randint(0, 59)))
    for offset, words, note_id in PLAN_USER2:
        if words <= 120:
            continue
        d = TODAY - datetime.timedelta(days=offset)
        vals.append("       (2, %d, '%s', %d, '%s %02d:%02d:00')" % (
            note_id, d.isoformat(), words, d.isoformat(), random.choice([10, 15, 21]), random.randint(0, 59)))
    lines.append("INSERT INTO `mn_writing_log` (`user_id`, `note_id`, `log_date`, `word_delta`, `create_time`)\n"
                 "VALUES\n" + ",\n".join(vals) + ";\n")
    lines.append("\n-- 生成信息：最近 %d 天，主账号书写天数 %d 天\n" % (
        DAYS, sum(1 for o, w, n in PLAN_USER1 if w > 0)))

    with io.open("sql/05_seed_misc.sql", "w", encoding="utf-8", newline="\n") as f:
        f.write("".join(lines))
    print("written sql/05_seed_misc.sql, log rows=%d" % len(vals))


if __name__ == "__main__":
    main()
