#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""批量生成 AI 模块的 DTO / VO（字段集中定义，保证注释与格式统一）。"""
import io
import os

ROOT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                    "src", "main", "java", "org", "example", "mynote")

DTO_TPL = """package org.example.mynote.dto;

{constraint_imports}import lombok.Data;

import java.io.Serializable;
{extra_imports}
/**
 * {doc}
 *
 * @author MyNote
 */
@Data
public class {name} implements Serializable {{

    private static final long serialVersionUID = 1L;

{fields}}}
"""

VO_TPL = """package org.example.mynote.vo;

import lombok.Data;

import java.io.Serializable;
{extra_imports}
/**
 * {doc}
 *
 * @author MyNote
 */
@Data
public class {name} implements Serializable {{

    private static final long serialVersionUID = 1L;

{fields}}}
"""

F = "    /**\n     * {comment}\n     */\n{annotations}    private {type} {name};\n\n"


def field(comment, type_, name, annotations=""):
    return F.format(comment=comment, type=type_, name=name,
                    annotations=("" if not annotations else annotations + "\n"))


def render(pkg_tpl, name, doc, fields, constraints="", extra_imports="", constraint_imports=""):
    return pkg_tpl.format(name=name, doc=doc, fields="".join(fields),
                          constraint_imports=constraint_imports, extra_imports=extra_imports)


DTOS = [
    ("AiChatDTO", "AI 对话入参。sessionId 为空表示开新会话。", [
        field("绑定的笔记 id，可为空（自由对话）", "Long", "noteId"),
        field("会话 id，为空则新建会话", "Long", "sessionId"),
        field("用户的问题", "String", "question",
              '    @NotBlank(message = "想问什么都还没写呢")\n'
              '    @Size(max = 2000, message = "单条提问不要超过 2000 字")'),
    ], "NotBlank,Size", "import jakarta.validation.constraints.NotBlank;\n"),
    ("AiActionDTO", "快捷写作动作入参。", [
        field("笔记 id", "Long", "noteId", '    @NotNull(message = "请先打开一篇笔记")'),
        field("动作：continue/polish/expand/shrink/title/summary/name/consistency/ending", "String", "action",
              '    @NotBlank(message = "要执行什么动作？")'),
        field("选中文字，为空时对整个笔记操作", "String", "selection"),
        field("作者的额外要求，例如「更冷一点」", "String", "instruction"),
        field("会话 id，用于延续上下文", "Long", "sessionId"),
    ], "NotBlank,NotNull", ""),
    ("AiRewriteDTO", "改写请求入参：返回新文本但不落库，等用户确认。", [
        field("笔记 id", "Long", "noteId", '    @NotNull(message = "请先打开一篇笔记")'),
        field("动作：polish/expand/shrink", "String", "action", '    @NotBlank(message = "缺少动作")'),
        field("要被替换的原文", "String", "selection", '    @NotBlank(message = "先选中要修改的文字")'),
        field("作者的额外要求", "String", "instruction"),
    ], "NotBlank,NotNull", ""),
    ("AiApplyDTO", "用户确认后的替换入参：把 revised 覆盖进笔记。", [
        field("笔记 id", "Long", "noteId", '    @NotNull(message = "缺少笔记 id")'),
        field("替换锚点原文", "String", "original", '    @NotBlank(message = "缺少替换锚点")'),
        field("替换后的新文本", "String", "revised", '    @NotBlank(message = "新文本为空，无法替换")'),
    ], "NotBlank,NotNull", ""),
    ("AiSuggestDTO", "飘窗建议入参。", [
        field("笔记 id", "Long", "noteId", '    @NotNull(message = "缺少笔记 id")'),
        field("光标附近或选中的文字，可空", "String", "selection"),
    ], "NotNull", ""),
    ("AiBindRefDTO", "把文献绑定到笔记。", [
        field("笔记 id", "Long", "noteId", '    @NotNull(message = "缺少笔记 id")'),
        field("文献 id，为 0 或空表示解除绑定", "Long", "refId", '    @NotNull(message = "缺少文献 id")'),
        field("来源：ai / manual", "String", "source"),
    ], "NotNull", ""),
    ("AiConfigSaveDTO", "新增或修改模型配置。apiKey 传空串表示不修改已有值。", [
        field("配置 id，为空表示新增", "Long", "id"),
        field("服务商标识", "String", "providerCode", '    @NotBlank(message = "请选择服务商")'),
        field("服务商展示名", "String", "providerName"),
        field("OpenAI 兼容接口地址", "String", "baseUrl", '    @NotBlank(message = "请填写接口地址")'),
        field("模型名", "String", "modelName", '    @NotBlank(message = "请填写模型名")'),
        field("API Key 明文，服务端加密后落库；传空串表示保持原值", "String", "apiKey"),
        field("采样温度 0~2", "java.math.BigDecimal", "temperature"),
        field("最大输出 token", "Integer", "maxTokens"),
        field("是否设为默认模型", "Boolean", "defaultConfig"),
    ], "NotBlank", ""),
    ("AiTestDTO", "连通性测试入参。传 id 用已存配置，否则用表单里的临时值。", [
        field("已存配置 id，可空", "Long", "id"),
        field("接口地址", "String", "baseUrl"),
        field("模型名", "String", "modelName"),
        field("API Key；以 **** 开头视为沿用已存值", "String", "apiKey"),
    ], "", ""),
    ("DiagramGenerateDTO", "多篇笔记生成逻辑图。", [
        field("参与生成的笔记 id", "java.util.List<Long>", "noteIds",
              '    @NotEmpty(message = "先在列表里勾选至少两篇笔记")'),
        field("图类型：mindmap/flowchart/graph/timeline", "String", "type"),
        field("是否调用模型重排结构；false 时走本地算法，离线可用", "Boolean", "useAi"),
    ], "NotEmpty", ""),
    ("DiagramSaveDTO", "保存生成好的逻辑图。", [
        field("标题", "String", "title", '    @NotBlank(message = "给这张图起个名字")'),
        field("图类型", "String", "diagramType"),
        field("参与笔记 id，逗号分隔", "String", "noteIds"),
        field("Mermaid 源码", "String", "code", '    @NotBlank(message = "图为空，无法保存")'),
    ], "NotBlank", ""),
]

VOS = [
    ("AiStatusVO", "助手可用状态。前端据此决定右侧面板显示「配置引导」还是「对话输入框」。", [
        field("是否已配置可用模型", "Boolean", "configured"),
        field("当前默认模型名", "String", "model"),
        field("服务商展示名", "String", "providerName"),
        field("离线也可用的能力清单", "java.util.List<String>", "offlineScenes"),
        field("需要模型的能力清单", "java.util.List<String>", "onlineScenes"),
    ]),
    ("AiConfigVO", "模型配置回显，apiKey 永远是掩码。", [
        field("配置 id", "Long", "id"),
        field("服务商标识", "String", "providerCode"),
        field("服务商展示名", "String", "providerName"),
        field("接口地址", "String", "baseUrl"),
        field("模型名", "String", "modelName"),
        field("API Key 掩码，未配置为 null", "String", "apiKeyMask"),
        field("是否已填写 Key", "Boolean", "hasKey"),
        field("采样温度", "java.math.BigDecimal", "temperature"),
        field("最大输出 token", "Integer", "maxTokens"),
        field("是否默认", "Boolean", "defaultConfig"),
        field("是否启用", "Boolean", "enabled"),
    ]),
    ("KnowledgeVO", "背景知识卡片。", [
        field("卡片 id", "Long", "id"),
        field("分类", "String", "category"),
        field("时代", "String", "era"),
        field("地域", "String", "region"),
        field("标题", "String", "title"),
        field("正文", "String", "content"),
        field("命中的关键词", "java.util.List<String>", "hitWords"),
        field("出处", "String", "source"),
        field("相关度分值", "Integer", "score"),
    ]),
    ("KnowledgeResultVO", "背景知识浮现结果。", [
        field("助手模式：research/life/creation", "String", "mode"),
        field("模式中文名", "String", "modeName"),
        field("命中的卡片", "java.util.List<KnowledgeVO>", "cards"),
        field("触发浮现的词", "java.util.List<String>", "triggerWords"),
        field("模型补充的提示（未配置模型时为 null）", "String", "aiHint"),
        field("说明文案，前端直接展示", "String", "reason"),
    ]),
    ("ReferenceVO", "文献条目。", [
        field("文献 id（联网结果为空，尚未入库）", "Long", "id"),
        field("标题", "String", "title"),
        field("作者", "String", "authors"),
        field("期刊/会议", "String", "journal"),
        field("年份", "Integer", "pubYear"),
        field("DOI", "String", "doi"),
        field("链接", "String", "url"),
        field("学科方向", "String", "field"),
        field("摘要或推荐理由", "String", "abstractText"),
        field("被引次数", "Integer", "citedCount"),
        field("命中的关键词", "java.util.List<String>", "matched"),
        field("相关度", "Integer", "score"),
        field("推荐理由（模型给出时为挑选理由，否则为规则命中的词）", "String", "reason"),
        field("是否已绑定到当前笔记", "Boolean", "bound"),
        field("来源：local / online", "String", "source"),
    ]),
    ("ReferenceResultVO", "文献推荐结果。", [
        field("助手模式", "String", "mode"),
        field("从笔记里抽出的检索词", "java.util.List<String>", "keywords"),
        field("文献列表", "java.util.List<ReferenceVO>", "items"),
        field("是否合并了联网检索结果", "Boolean", "online"),
        field("说明文案", "String", "reason"),
    ]),
    ("RewriteVO", "改写预览。前端据此渲染 diff，用户确认后再调 apply。", [
        field("原文", "String", "original"),
        field("新文本", "String", "revised"),
        field("改动说明", "String", "explanation"),
        field("使用的模型名", "String", "model"),
    ]),
    ("SuggestVO", "飘窗建议。", [
        field("建议问题", "java.util.List<String>", "suggestions"),
        field("助手模式", "String", "mode"),
        field("来源：ai / local", "String", "source"),
    ]),
    ("AiActionVO", "快捷动作结果。", [
        field("动作", "String", "action"),
        field("模型输出", "String", "text"),
        field("使用的模型名", "String", "model"),
    ]),
    ("AiSessionVO", "会话概要。", [
        field("会话 id", "Long", "id"),
        field("绑定笔记 id", "Long", "noteId"),
        field("标题", "String", "title"),
        field("消息条数", "Integer", "messageCount"),
        field("最后活跃时间", "java.time.LocalDateTime", "updateTime"),
    ]),
    ("AiMessageVO", "会话消息。", [
        field("消息 id", "Long", "id"),
        field("角色 user/assistant", "String", "role"),
        field("内容", "String", "content"),
        field("场景", "String", "scene"),
        field("时间", "java.time.LocalDateTime", "createTime"),
    ]),
    ("DiagramVO", "逻辑图。", [
        field("图 id", "Long", "id"),
        field("标题", "String", "title"),
        field("图类型", "String", "diagramType"),
        field("参与笔记 id 串", "String", "noteIds"),
        field("Mermaid 源码", "String", "code"),
        field("生成方式：ai / local", "String", "generator"),
        field("创建时间", "java.time.LocalDateTime", "createTime"),
    ]),
]


def main():
    dto_dir = os.path.join(ROOT, "dto")
    vo_dir = os.path.join(ROOT, "vo")
    for d in (dto_dir, vo_dir):
        if not os.path.isdir(d):
            os.makedirs(d)
    for name, doc, fields, constraints, extra in DTOS:
        needed = set()
        for f in fields:
            for c in ("NotBlank", "NotNull", "Size", "NotEmpty"):
                if c + "(" in f:
                    needed.add(c)
        constraint_imports = "".join(
            "import jakarta.validation.constraints." + c + ";" + chr(10) for c in sorted(needed))
        content = render(DTO_TPL, name, doc, fields, "", extra, constraint_imports)
        io.open(os.path.join(dto_dir, name + ".java"), "w", encoding="utf-8", newline="\n").write(content)
        print("dto/" + name)
    for name, doc, fields in VOS:
        content = render(VO_TPL, name, doc, fields)
        io.open(os.path.join(vo_dir, name + ".java"), "w", encoding="utf-8", newline="\n").write(content)
        print("vo/" + name)


if __name__ == "__main__":
    main()
