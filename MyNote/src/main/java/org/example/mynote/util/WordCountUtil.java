package org.example.mynote.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 字数统计工具。
 *
 * <p>创作类产品的"字数"必须与作者习惯一致：中文按字计数，英文与数字按词计数，
 * Markdown 语法符号与代码块不计入。前端编辑器同样按这条规则统计，两者结果可比对。
 * 规则改动时需同步 tools/sync_word_count.py，否则示例数据会与接口口径不一致。</p>
 *
 * @author MyNote
 */
public final class WordCountUtil {

    /**
     * 中日韩字符（含平假名、片假名、谚文）
     */
    private static final Pattern CJK = Pattern.compile("[㐀-䶿一-鿿぀-ヿ가-힯]");

    /**
     * 西文单词与数字串
     */
    private static final Pattern LATIN_WORD = Pattern.compile("[A-Za-z0-9À-ſ]+");

    private static final Pattern CODE_BLOCK = Pattern.compile("```.*?```", Pattern.DOTALL);

    private static final Pattern INLINE_CODE = Pattern.compile("`([^`]*)`");

    private static final Pattern IMAGE = Pattern.compile("!\\[[^\\]]*\\]\\([^)]*\\)");

    private static final Pattern LINK = Pattern.compile("\\[([^\\]]*)\\]\\([^)]*\\)");

    private static final Pattern HEADING = Pattern.compile("(?m)^\\s{0,3}#{1,6}\\s*");

    private static final Pattern QUOTE = Pattern.compile("(?m)^\\s{0,3}>\\s?");

    private static final Pattern UNORDERED_LIST = Pattern.compile("(?m)^\\s*[-+*]\\s+(\\[[ xX]\\]\\s*)?");

    private static final Pattern ORDERED_LIST = Pattern.compile("(?m)^\\s*\\d+\\.\\s+");

    private static final Pattern TABLE_MARK = Pattern.compile("[|:*_~`]+");

    private static final Pattern DIVIDER = Pattern.compile("(?m)^\\s*[-=]{3,}\\s*$");

    /**
     * 块级公式 $$...$$，数学符号不计入字数
     */
    private static final Pattern BLOCK_MATH = Pattern.compile("\\$\\$.*?\\$\\$", Pattern.DOTALL);

    /**
     * 行内公式 $...$
     */
    private static final Pattern INLINE_MATH = Pattern.compile("\\$[^$\\n]{0,200}\\$");

    /**
     * LaTeX 命令名（\bigoplus、\in 之类），是排版指令而不是内容
     */
    private static final Pattern TEX_COMMAND = Pattern.compile("\\\\[A-Za-z]+");

    private WordCountUtil() {
    }

    /**
     * 去掉 Markdown 语法标记，返回可检索的纯文本。
     *
     * @param markdown Markdown 源文本
     * @return 纯文本
     */
    public static String stripMarkdown(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }
        String text = markdown.replace("\r", "");
        text = CODE_BLOCK.matcher(text).replaceAll(" ");
        text = INLINE_CODE.matcher(text).replaceAll("$1");
        text = IMAGE.matcher(text).replaceAll(" ");
        text = LINK.matcher(text).replaceAll("$1");
        text = HEADING.matcher(text).replaceAll("");
        text = QUOTE.matcher(text).replaceAll("");
        text = UNORDERED_LIST.matcher(text).replaceAll("");
        text = ORDERED_LIST.matcher(text).replaceAll("");
        // 公式要在剥表格符号之前处理，否则下划线先被吃掉会让 $$ 匹配不上
        text = BLOCK_MATH.matcher(text).replaceAll(" ");
        text = INLINE_MATH.matcher(text).replaceAll(" ");
        text = TEX_COMMAND.matcher(text).replaceAll(" ");
        text = DIVIDER.matcher(text).replaceAll("");
        text = TABLE_MARK.matcher(text).replaceAll("");
        return text;
    }

    /**
     * 统计字数：中文字符逐字计数，西文与数字按词计数。
     *
     * @param markdown Markdown 源文本
     * @return 字数
     */
    public static int count(String markdown) {
        String text = stripMarkdown(markdown);
        if (text.isEmpty()) {
            return 0;
        }
        int cjk = 0;
        Matcher cjkMatcher = CJK.matcher(text);
        while (cjkMatcher.find()) {
            cjk++;
        }
        int latin = 0;
        Matcher wordMatcher = LATIN_WORD.matcher(CJK.matcher(text).replaceAll(" "));
        while (wordMatcher.find()) {
            latin++;
        }
        return cjk + latin;
    }

    /**
     * 生成笔记摘要：优先取正文首个非标题段落，压缩空白后截断。
     *
     * @param markdown Markdown 源文本
     * @param maxLength 最大长度
     * @return 摘要
     */
    public static String summarize(String markdown, int maxLength) {
        String plain = stripMarkdown(markdown).replaceAll("\\s+", " ").trim();
        if (plain.isEmpty()) {
            return "";
        }
        if (plain.length() <= maxLength) {
            return plain;
        }
        return plain.substring(0, maxLength) + "...";
    }

    /**
     * 按行取第一个非空行，用于从正文推断未命名笔记的标题。
     *
     * @param markdown Markdown 源文本
     * @return 首个非空行；全为空时返回空串
     */
    public static String firstLine(String markdown) {
        if (markdown == null) {
            return "";
        }
        for (String line : markdown.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return "";
    }

    /**
     * 提取标题行中的文字，去掉前置的 # 与 Emoji 装饰。
     *
     * @param line 原始行
     * @return 干净标题
     */
    public static String cleanTitle(String line) {
        if (line == null) {
            return "";
        }
        String title = HEADING.matcher(line).replaceAll("").trim();
        title = title.replaceAll("^《|》$", "");
        if (title.length() > 100) {
            title = title.substring(0, 100);
        }
        return title;
    }

    /**
     * 安全截断，避免超长文本进入提示词。
     *
     * @param text      原文
     * @param maxLength 最大长度
     * @return 截断结果
     */
    public static String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}
