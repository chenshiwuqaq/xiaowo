package org.example.mynote.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 字数统计与 Markdown 剥离测试。
 *
 * <p>字数是这款产品里少数"错了会被用户一眼看出来"的数字：主页看板、编辑器状态栏、
 * 目标进度三处共用同一口径。规则改动时必须同步 tools/sync_word_count.py，
 * 否则示例数据的 word_count 会和接口算出来的不一致。</p>
 *
 * @author MyNote
 */
class WordCountUtilTest {

    /**
     * LaTeX 反斜杠。写在源码里容易被转义规则干扰，这里显式构造。
     */
    private static final String BS = String.valueOf((char) 92);

    @Test
    @DisplayName("中文逐字计数")
    void countsChineseByCharacter() {
        assertEquals(7, WordCountUtil.count("长安城醒在最黑"));
        assertEquals(2, WordCountUtil.count("你好"));
    }

    @Test
    @DisplayName("西文与数字按词计数")
    void countsLatinByWord() {
        assertEquals(3, WordCountUtil.count("graph neural network"));
        assertEquals(2, WordCountUtil.count("GNN 2017"));
    }

    @Test
    @DisplayName("中英混排时两种计数相加")
    void countsMixedText() {
        // "写了六个字" 5 字 + GraphSAGE 1 词 = 6
        assertEquals(6, WordCountUtil.count("写了六个字 GraphSAGE"));
    }

    @Test
    @DisplayName("Markdown 语法符号与代码块不计入字数")
    void ignoresMarkupAndCode() {
        String withMarkup = "# 标题\n\n> 引用一行\n\n```java\npublic void hello() {}\n```\n\n- [ ] 待办项\n";
        int plainOnly = WordCountUtil.count("标题引用一行待办项");
        assertEquals(plainOnly, WordCountUtil.count(withMarkup), "语法符号与代码块应完全不计入");
    }

    @Test
    @DisplayName("链接与图片只算可见文字")
    void countsLinkTextOnly() {
        assertEquals(2, WordCountUtil.count("[说明](https://example.com/a.png)"));
        // 图片是"内容占位"而不是文字，alt 不计入字数，与 Typora 等编辑器的口径一致
        assertEquals(0, WordCountUtil.count("![替代文字](/upload/a.png)"));
    }

    @Test
    @DisplayName("公式与 LaTeX 命令不计入字数")
    void ignoresMath() {
        assertEquals(6, WordCountUtil.count("聚合算子选择"));
        String withMath = "聚合算子选择 $$" + BS + "bigoplus_{u " + BS + "in N(v)}$$ 很关键";
        // 6（聚合算子选择）+ 3（很关键）：公式符号与命令名一个字都不该算
        assertEquals(9, WordCountUtil.count(withMath), "公式被计入了字数");
        assertFalse(WordCountUtil.stripMarkdown(withMath).contains("bigoplus"));
    }

    @Test
    @DisplayName("空输入返回 0 且不抛异常")
    void handlesNullAndBlank() {
        assertEquals(0, WordCountUtil.count(null));
        assertEquals(0, WordCountUtil.count(""));
        assertEquals(0, WordCountUtil.count("   \n  "));
        assertEquals("", WordCountUtil.summarize(null, 20));
    }

    @Test
    @DisplayName("摘要压缩空白并按上限截断")
    void summarizeTruncates() {
        String text = "# 长文\n\n第一段内容。\n\n第二段内容。";
        String summary = WordCountUtil.summarize(text, 8);
        assertTrue(summary.length() <= 11, "摘要应被截断，实际长度=" + summary.length());
        assertFalse(summary.contains("\n"), "摘要不应保留换行");
    }

    @Test
    @DisplayName("firstLine 取行不剥语法，cleanTitle 负责剥")
    void titleHelpers() {
        assertEquals("# 第一章 上元夜", WordCountUtil.firstLine("\n\n# 第一章 上元夜\n正文"));
        assertEquals("第一章 上元夜", WordCountUtil.cleanTitle(WordCountUtil.firstLine("\n\n# 第一章 上元夜\n正文")));
        assertEquals("第一章 上元夜", WordCountUtil.cleanTitle("## 第一章 上元夜"));
        assertEquals("", WordCountUtil.cleanTitle(null));
    }

    @Test
    @DisplayName("truncate 对超长文本加省略号，对 null 返回空串")
    void truncateWorks() {
        assertEquals("", WordCountUtil.truncate(null, 10));
        assertEquals("abc", WordCountUtil.truncate("abc", 10));
        assertEquals("abcdefg...", WordCountUtil.truncate("abcdefghij", 7));
    }
}
