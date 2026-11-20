#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
按后端 WordCountUtil 的同一套规则回填示例笔记的 word_count，
避免主页统计与编辑器字数条对不上。

规则（与 Java 实现保持一致，改动需同步）：
  1. 去掉代码块 / 行内代码；
  2. 链接与图片只保留可见文字（[文字](地址) -> 文字，![替代](地址) -> 替代）；
  3. 去掉行首的 Markdown 标记（# > - * | 数字. [] 等）；
  4. 中日韩字符逐字计数，西文与数字按词计数。
"""
import io
import re
import subprocess
import sys

MYSQL = r"D:/Mysql/mysql-5.7.42-winx64/bin/mysql.exe"
CJK = re.compile(u"[\u3400-\u4dbf\u4e00-\u9fff\u3040-\u30ff\uac00-\ud7af]")
WORD = re.compile(u"[A-Za-z0-9\u00c0-\u017f]+")

STRIP_RULES = [
    (re.compile(r"```.*?```", re.S), ""), (re.compile(r"`([^`]*)`"), r"\1"),
    (re.compile(r"!\[[^\]]*\]\([^)]*\)"), ""), (re.compile(r"\[([^\]]*)\]\([^)]*\)"), r"\1"),
    (re.compile(r"(?m)^\s{0,3}#{1,6}\s*"), ""), (re.compile(r"(?m)^\s{0,3}>\s?"), ""),
    (re.compile(r"(?m)^\s*[-+*]\s+(\[[ xX]\]\s*)?"), ""), (re.compile(r"(?m)^\s*\d+\.\s+"), ""),
    (re.compile(r"\$\$.*?\$\$", re.S), " "), (re.compile(r"\$[^$\n]{0,200}\$"), " "),
    (re.compile(r"\\[A-Za-z]+"), " "),
    (re.compile(r"[|:*_~`]+"), ""), (re.compile(r"(?m)^\s*[-=]{3,}\s*$"), ""),
]


def count(text):
    if not text:
        return 0
    t = text.replace("\r", "")
    for pat, rep in STRIP_RULES:
        t = pat.sub(rep, t)
    cjk = len(CJK.findall(t))
    latin = len(WORD.findall(CJK.sub(" ", t)))
    return cjk + latin


def sql_str(s):
    return s.replace("\\", "\\\\").replace("'", "''")


def main():
    only_select = ("SELECT id, title, content FROM mn_note ORDER BY id")
    out = subprocess.run([MYSQL, "-uroot", "-p20050702cj", "--default-character-set=utf8mb4", "-N", "-B",
                          "-e", only_select, "newnote"], capture_output=True)
    rows = out.stdout.decode("utf-8", "replace").split("\n")
    stmts = []
    for line in rows:
        if not line.strip():
            continue
        parts = line.split("\t")
        if len(parts) < 3:
            continue
        nid, title, content = parts[0], parts[1], "\t".join(parts[2:]).replace("\\n", "\n")
        c = count(content)
        stmts.append("UPDATE `mn_note` SET `word_count` = %d WHERE `id` = %s;" % (c, nid))
        print("%-4s %-34s %d" % (nid, title[:34], c))
    with io.open("sql/06_sync_word_count.sql", "w", encoding="utf-8", newline="\n") as f:
        f.write("-- 由 tools/sync_word_count.py 生成：按后端同一规则回填示例笔记字数\nUSE `newnote`;\n\n")
        f.write("\n".join(stmts))
    print("written sql/06_sync_word_count.sql")


if __name__ == "__main__":
    main()
