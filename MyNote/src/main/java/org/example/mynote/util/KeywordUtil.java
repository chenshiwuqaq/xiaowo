package org.example.mynote.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 关键词提取工具。
 *
 * <p>中文没有词边界。常规做法是接分词器（HanLP、jieba 之类），但那要么引入上百 MB 词典，
 * 要么依赖外部服务——与"单机跑起来就行"的前提冲突。这里用两条互补的线索取词，
 * 只依赖文本自身：</p>
 *
 * <ol>
 *   <li><b>高频 n-gram</b>：对连续中文取 2~6 字子串，只保留出现两次以上的候选。
 *       一个串反复出现，基本就是本文的主题词（"图神经网络""金吾卫"）；</li>
 *   <li><b>虚词切段</b>：把连续中文按"的/了/是/与/而…"这类结构助词切开，
 *       段本身就是一个候选词（"图神经网络的核心" → "图神经网络" + "核心"）。
 *       这条线索负责捞只出现过一次、但边界明确的词——地名、官职名、术语往往如此。</li>
 * </ol>
 *
 * <p>两条线索都再做一次"首尾搭接"去重：选中"图神经网络"后，
 * "网络研究综述"这类错位碎片会被丢掉。只靠 n-gram 加权重是压不住碎片的，
 * 这是反复验证后留下的结论。</p>
 *
 * <p>提取结果同时用于三处：文献检索词、背景知识命中、逻辑图节点标签。</p>
 *
 * @author MyNote
 */
public final class KeywordUtil {

    private static final Pattern CJK_RUN = Pattern.compile("[㐀-䶿一-鿿]+");

    private static final Pattern LATIN_RUN = Pattern.compile("[A-Za-z][A-Za-z0-9+.\\-]{2,}");

    /**
     * n-gram 候选的长度范围
     */
    private static final int MIN_GRAM = 2;

    private static final int MAX_GRAM = 6;

    /**
     * n-gram 进入结果所需的最低出现次数：出现两次以上才算真主题
     */
    private static final int MIN_REPEAT = 2;

    /**
     * 虚词切段候选的长度上限，超过视为整句未切开
     */
    private static final int MAX_SEGMENT = 8;

    /**
     * 自由度上限，再大也不额外加分，避免长句被当成词
     */
    private static final int FREEDOM_CAP = 6;

    /**
     * 西文候选的最小长度，两字母多为缩写噪声
     */
    private static final int MIN_LATIN_LENGTH = 3;

    /**
     * 结构助词与连接性单字。既用作切分点，也用于否决：
     * 一个候选词内部若含这些字，几乎可以断定它是句子片段而不是词。
     */
    private static final String FUNCTION_CHARS = "的了在是和与而就也被把让到从对为以但是很都只还又给上下里中之其此该等及或若于";

    /**
     * 段首常挂着的动词。它们不是助词，但同样不该出现在检索词里。
     */
    private static final String[] PREFIX_NOISE = {"引入", "使用", "采用", "通过", "进行", "实现", "完成", "提出",
            "需要", "可以", "能够", "作为", "以及", "并且", "其中", "如下", "例如"};

    /**
     * 段尾常挂着的位置词与抽象后缀，检索时是纯噪声
     */
    private static final String[] SUFFIX_NOISE = {"方面", "问题", "情况", "方式", "内容", "北边", "南边", "东边",
            "西边", "里面", "外面", "上面", "下面", "之前", "之后", "之中", "一类", "之类"};

    /**
     * 中英停用词
     */
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "的", "了", "在", "是", "和", "与", "或", "就", "也", "都", "很", "把", "被", "让", "到", "从", "对",
            "为", "以", "而", "但", "如果", "因为", "所以", "但是", "然后", "可以", "可能", "已经", "自己",
            "什么", "怎么", "这里", "那里", "一个", "一些", "这样", "那样", "还有", "不是", "以及", "其中",
            "通过", "进行", "使用", "需要", "能够", "应该", "如下", "例如", "比如", "总之", "因此", "并且",
            "不过", "其实", "确实", "我们", "你们", "他们", "它们", "用于", "目的", "方面", "情况", "问题",
            "内容", "方式", "过程", "时候", "东西", "一下", "一种", "一点",
            "the", "and", "for", "with", "that", "this", "from", "are", "was", "were", "have", "has", "not",
            "but", "you", "your", "they", "them", "then", "than", "also", "into", "over", "such", "can", "will",
            "both", "each", "every", "rely", "relies", "mean", "means", "using", "used", "use", "via", "per",
            "these", "those", "there", "their", "would", "could", "should", "about", "which", "while", "where"));

    private KeywordUtil() {
    }

    /**
     * 候选词及其统计证据。
     */
    private static final class Candidate {

        private final String word;

        private final Map<Character, Integer> leftNeighbors = new HashMap<>(8);

        private final Map<Character, Integer> rightNeighbors = new HashMap<>(8);

        private int freq;

        /**
         * 词来自哪条线索：true 表示由虚词切段得到，边界更可信
         */
        private boolean segmented;

        private double cohesion = 1.0D;

        Candidate(String word) {
            this.word = word;
        }

        /**
         * 权重 = 频次 × 长度² ×（1 + 自由度）×（1 + 内聚度），切段来源再乘 1.5。
         *
         * <p>长度取平方是刻意的：中文 n-gram 会产生大量互为子串的碎片（"图神经网络"/"图神经"/"经网络"），
         * 只按频次乘长度的话，出现两次的短词会压过只出现一次的完整术语。</p>
         *
         * @return 权重
         */
        double weight() {
            int freedom = Math.min(leftNeighbors.size(), rightNeighbors.size());
            freedom = Math.min(Math.max(freedom, 1), FREEDOM_CAP);
            double base = freq * word.length() * word.length() * (1.0D + freedom)
                    * (1.0D + Math.max(cohesion, 0.0D));
            return segmented ? base * 1.5D : base;
        }
    }

    /**
     * 提取关键词，按权重降序返回。
     *
     * @param text     文本（Markdown 源文本亦可，内部先剥离语法）
     * @param maxCount 返回个数
     * @return 关键词列表，无可用词时返回空列表
     */
    public static List<String> extract(String text, int maxCount) {
        if (text == null || text.isEmpty() || maxCount <= 0) {
            return List.of();
        }
        String plain = WordCountUtil.stripMarkdown(text);
        Map<String, Candidate> ngrams = new LinkedHashMap<>();
        Map<String, Candidate> segments = new LinkedHashMap<>();
        collectNgrams(plain, ngrams);
        collectSegments(plain, segments);
        collectLatin(plain, ngrams);
        refineCohesion(ngrams);

        List<String> result = new ArrayList<>(maxCount);
        Map<String, Candidate> picked = new LinkedHashMap<>();
        // 先取重复出现的 n-gram（证据最强），再用虚词切段补边界清楚但只出现一次的词
        takeRanked(sort(ngrams), picked, result, maxCount, MIN_REPEAT, MAX_GRAM);
        takeRanked(sort(segments), picked, result, maxCount, 1, MAX_SEGMENT);
        if (result.size() < maxCount) {
            takeRanked(sort(ngrams), picked, result, maxCount, 1, 4);
        }
        return result;
    }

    /**
     * 按权重排序。
     *
     * @param source 候选表
     * @return 排序后的候选
     */
    private static List<Candidate> sort(Map<String, Candidate> source) {
        List<Candidate> ordered = new ArrayList<>(source.values());
        ordered.sort(Comparator.comparingDouble(Candidate::weight).reversed());
        return ordered;
    }

    /**
     * 从一组候选里按条件取词。
     *
     * @param ordered  已排序候选
     * @param picked   已选集合（跨轮共享）
     * @param result   输出
     * @param maxCount 总数上限
     * @param minFreq  最低出现次数
     * @param maxLen   最大长度
     */
    private static void takeRanked(List<Candidate> ordered, Map<String, Candidate> picked, List<String> result,
                                   int maxCount, int minFreq, int maxLen) {
        for (Candidate candidate : ordered) {
            String word = candidate.word;
            if (result.size() >= maxCount) {
                return;
            }
            if (candidate.freq < minFreq || word.length() > maxLen || STOP_WORDS.contains(word)
                    || covered(candidate, picked)) {
                continue;
            }
            result.add(word);
            picked.put(word, candidate);
        }
    }

    /**
     * 收集 2~6 字 n-gram 及其左右邻字。
     *
     * @param plain   纯文本
     * @param ngrams  候选表
     */
    private static void collectNgrams(String plain, Map<String, Candidate> ngrams) {
        List<String> runs = cjkRuns(plain);
        for (int r = 0; r < runs.size(); r++) {
            String run = runs.get(r);
            char leftGlue = r > 0 ? runs.get(r - 1).charAt(runs.get(r - 1).length() - 1) : 0;
            char rightGlue = r + 1 < runs.size() ? runs.get(r + 1).charAt(0) : 0;
            for (int size = MIN_GRAM; size <= MAX_GRAM && size <= run.length(); size++) {
                for (int i = 0; i + size <= run.length(); i++) {
                    String word = run.substring(i, i + size);
                    if (containsFunctionChar(word) || STOP_WORDS.contains(word)) {
                        continue;
                    }
                    Candidate candidate = ngrams.computeIfAbsent(word, Candidate::new);
                    candidate.freq++;
                    char before = i > 0 ? run.charAt(i - 1) : leftGlue;
                    char after = i + size < run.length() ? run.charAt(i + size) : rightGlue;
                    if (before != 0) {
                        candidate.leftNeighbors.merge(before, 1, Integer::sum);
                    }
                    if (after != 0) {
                        candidate.rightNeighbors.merge(after, 1, Integer::sum);
                    }
                }
            }
        }
    }

    /**
     * 按结构助词切段取词。
     *
     * <p>"图神经网络的核心是消息传递框架" 切成 "图神经网络" 与 "核心" 与 "消息传递框架"——
     * 助词位置就是最可靠的词边界证据。切出的段仍要求内部不含助词，长度超过
     * {@value #MAX_SEGMENT} 的段说明这句没切开，直接放弃。</p>
     *
     * @param plain     纯文本
     * @param segments  候选表
     */
    private static void collectSegments(String plain, Map<String, Candidate> segments) {
        for (String run : cjkRuns(plain)) {
            int start = 0;
            for (int i = 0; i <= run.length(); i++) {
                boolean cut = i == run.length() || FUNCTION_CHARS.indexOf(run.charAt(i)) >= 0;
                if (!cut) {
                    continue;
                }
                String seg = trimNoise(run.substring(start, i));
                start = i + 1;
                if (seg.length() < MIN_GRAM || seg.length() > MAX_SEGMENT || STOP_WORDS.contains(seg)) {
                    continue;
                }
                Candidate candidate = segments.computeIfAbsent(seg, Candidate::new);
                candidate.freq++;
                candidate.segmented = true;
            }
        }
    }

    /**
     * 收集西文候选。西文有天然边界，按词计即可。
     *
     * @param plain    纯文本
     * @param ngrams   候选表
     */
    private static void collectLatin(String plain, Map<String, Candidate> ngrams) {
        Matcher matcher = LATIN_RUN.matcher(plain);
        while (matcher.find()) {
            String word = matcher.group().toLowerCase();
            if (word.length() < MIN_LATIN_LENGTH || STOP_WORDS.contains(word)) {
                continue;
            }
            Candidate candidate = ngrams.computeIfAbsent(word, Candidate::new);
            candidate.freq++;
            candidate.leftNeighbors.merge('a', 1, Integer::sum);
            candidate.rightNeighbors.merge('b', 1, Integer::sum);
            candidate.cohesion = 2.0D;
        }
    }

    /**
     * 计算内聚度：取所有切分法里最差的一刀。
     *
     * <p>只要存在一种切法，其左右两半各自都远比整词常见，这个"词"就不成立。</p>
     *
     * @param ngrams 候选表
     */
    private static void refineCohesion(Map<String, Candidate> ngrams) {
        for (Candidate candidate : new ArrayList<>(ngrams.values())) {
            String word = candidate.word;
            if (word.length() < 3 || candidate.segmented) {
                candidate.cohesion = 1.0D;
                continue;
            }
            double worst = Double.MAX_VALUE;
            for (int cut = 1; cut < word.length(); cut++) {
                Candidate left = ngrams.get(word.substring(0, cut));
                Candidate right = ngrams.get(word.substring(cut));
                if (left == null || right == null) {
                    continue;
                }
                double expected = Math.max(1, left.freq) * Math.max(1, right.freq);
                worst = Math.min(worst, (double) candidate.freq * candidate.freq / expected);
            }
            candidate.cohesion = worst == Double.MAX_VALUE ? 1.0D : Math.min(worst, 8.0D) - 1.0D;
        }
    }

    /**
     * 剥掉段首动词与段尾位置词。
     *
     * <p>"引入注意力机制" 检索起来等价于 "注意力机制"，"可扩展性方面" 等价于 "可扩展性"。
     * 这一层清洗让无词典提取出来的词更接近可直接使用的检索词。</p>
     *
     * @param segment 原始段
     * @return 清洗后的段
     */
    private static String trimNoise(String segment) {
        String seg = segment;
        boolean changed = true;
        while (changed && seg.length() > MIN_GRAM) {
            changed = false;
            for (String prefix : PREFIX_NOISE) {
                if (seg.startsWith(prefix) && seg.length() - prefix.length() >= MIN_GRAM) {
                    seg = seg.substring(prefix.length());
                    changed = true;
                    break;
                }
            }
            if (changed) {
                continue;
            }
            for (String suffix : SUFFIX_NOISE) {
                if (seg.endsWith(suffix) && seg.length() - suffix.length() >= MIN_GRAM) {
                    seg = seg.substring(0, seg.length() - suffix.length());
                    changed = true;
                    break;
                }
            }
        }
        return seg;
    }

    /**
     * 取出全部连续中文段。
     *
     * @param plain 文本
     * @return 段落列表
     */
    private static List<String> cjkRuns(String plain) {
        List<String> runs = new ArrayList<>();
        Matcher matcher = CJK_RUN.matcher(plain);
        while (matcher.find()) {
            runs.add(matcher.group());
        }
        return runs;
    }

    /**
     * 候选内含结构助词即判为句子片段。
     *
     * @param word 候选
     * @return true 表示丢弃
     */
    private static boolean containsFunctionChar(String word) {
        for (int i = 0; i < word.length(); i++) {
            if (FUNCTION_CHARS.indexOf(word.charAt(i)) >= 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断候选是否与已选词重复（包含、被包含或首尾搭接）。
     *
     * @param candidate 待判断候选
     * @param picked    已选集合
     * @return true 表示应跳过
     */
    private static boolean covered(Candidate candidate, Map<String, Candidate> picked) {
        String word = candidate.word;
        for (Candidate chosen : picked.values()) {
            if (chosen.word.contains(word) || word.contains(chosen.word)) {
                return true;
            }
            int overlap = overlapLength(chosen.word, word);
            if (overlap >= 2 && (overlap >= 3 || (chosen.freq <= 1 && candidate.freq <= 1))) {
                return true;
            }
        }
        return false;
    }

    /**
     * a 的结尾与 b 的开头（或反向）的最大重叠长度。
     *
     * @param a 先选中的词
     * @param b 待判断的词
     * @return 重叠字数
     */
    private static int overlapLength(String a, String b) {
        int max = Math.min(Math.min(a.length(), b.length()), 4);
        for (int k = max; k >= 2; k--) {
            if (a.endsWith(b.substring(0, k)) || b.endsWith(a.substring(0, k))) {
                return k;
            }
        }
        return 0;
    }

    /**
     * 统计关键词在文本中的命中次数，并回填命中的词。
     *
     * @param text     文本
     * @param keywords 逗号分隔关键词串
     * @param hits     输出参数
     * @return 命中总次数
     */
    public static int countHits(String text, String keywords, List<String> hits) {
        if (text == null || keywords == null) {
            return 0;
        }
        int total = 0;
        for (String keyword : keywords.split(",")) {
            String word = keyword.trim();
            if (word.isEmpty()) {
                continue;
            }
            int hitsOfWord = 0;
            int index = text.indexOf(word);
            while (index >= 0) {
                hitsOfWord++;
                index = text.indexOf(word, index + word.length());
            }
            total += hitsOfWord;
            if (hitsOfWord > 0 && hits != null && !hits.contains(word)) {
                hits.add(word);
            }
        }
        return total;
    }

    /**
     * 判断关键词是否真的出现在候选文献的可检索字段里。
     *
     * <p>联网检索接口按被引数排序，不设这道闸门时一篇高被引的无关论文也会挤进推荐列表。</p>
     *
     * @param keyword 关键词
     * @param fields  待匹配字段
     * @return true 表示相关
     */
    public static boolean appearsIn(String keyword, String... fields) {
        if (keyword == null || keyword.isBlank()) {
            return false;
        }
        String lowerKeyword = keyword.toLowerCase();
        for (String field : fields) {
            if (field == null || field.isEmpty()) {
                continue;
            }
            String lowerField = field.toLowerCase();
            if (lowerField.contains(lowerKeyword)) {
                return true;
            }
            // 中文检索词命中英文语料本就勉强，退一步取前 4 字做存在性检查
            if (keyword.length() > 4 && lowerField.contains(lowerKeyword.substring(0, 4))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 拼检索串。
     *
     * @param keywords 关键词
     * @return 空格分隔
     */
    public static String joinQuery(List<String> keywords) {
        return String.join(" ", keywords);
    }

    /**
     * 词频表，供前端展示"这段在讲什么"。
     *
     * @param text 文本
     * @return 词 → 频次
     */
    public static Map<String, Integer> frequency(String text) {
        String plain = WordCountUtil.stripMarkdown(text == null ? "" : text);
        Map<String, Candidate> segments = new LinkedHashMap<>();
        collectSegments(plain, segments);
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Candidate candidate : segments.values()) {
            if (candidate.freq >= MIN_REPEAT && !STOP_WORDS.contains(candidate.word)) {
                result.put(candidate.word, candidate.freq);
            }
        }
        return result;
    }
}
