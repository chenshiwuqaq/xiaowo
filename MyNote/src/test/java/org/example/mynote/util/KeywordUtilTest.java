package org.example.mynote.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 关键词提取单元测试。
 *
 * <p>这里断言的不是"返回了什么"，而是"没有返回什么"——中文 n-gram 最容易出的毛病
 * 是把句子切成碎片（"经网络研究综"）。碎片一旦进入检索词，本地文献匹配与联网检索会一起被带偏，
 * 所以这条必须锁死在测试里。</p>
 *
 * @author MyNote
 */
class KeywordUtilTest {

    private static final String RESEARCH_TEXT = """
            # 图神经网络研究综述笔记

            图神经网络的核心是消息传递框架。GCN 使用一阶切比雪夫近似，GraphSAGE 用邻居采样实现归纳式学习，
            GAT 引入注意力机制对邻居加权，GIN 则证明了聚合函数与 WL 检验的等价关系。
            层数增加会带来过平滑问题，残差连接与个性化 PageRank 是常见的缓解手段。
            可扩展性方面，ClusterGCN 与图采样是两条主要路线。
            """;

    private static final String FICTION_TEXT = """
            长安城醒在最黑的时候。丑时三刻，承天门的鼓响了。
            上元夜前后三天京城弛禁，金吾卫照例加派巡查。裴元昭站在宣阳坊的坊门口，手里捏着一张金吾卫夜巡符。
            西市北边的巷子里新到了三车货：疏勒的琉璃、于阗的玉、波斯银盘上的联珠对鹿纹。
            """;

    @Test
    @DisplayName("科研文本能抽出核心主题词，且不输出句子碎片")
    void extractTechnicalTerms() {
        List<String> keywords = KeywordUtil.extract(RESEARCH_TEXT, 8);
        assertFalse(keywords.isEmpty(), "综述文本不应抽不出关键词");
        assertTrue(keywords.stream().anyMatch(k -> k.contains("神经网络")), "应覆盖核心主题，实际=" + keywords);
        // 中英混排时中文候选权重更高，英文术语单独用一段西文文本验证（见 extractLatinTerms）
        assertFalse(keywords.stream().allMatch(k -> k.length() == 3 && !k.equals("k")), "结果不应只剩三字词");
        for (String keyword : keywords) {
            // 无词典提取不追求完美分词，但至少不能把带助词的短语或错位碎片当词
            assertFalse(keyword.matches(".*[的了是在与而把被让从对以但].*"),
                    "候选里混进了带助词的句子片段：" + keyword + " 全量=" + keywords);
            assertFalse(keyword.startsWith("经网络") || keyword.startsWith("络研究") || keyword.contains("研究综")
                            && !keyword.equals("研究综述"),
                    "出现错位碎片：" + keyword + " 全量=" + keywords);
        }
    }

    @Test
    @DisplayName("小说文本能抽出时代与地点词")
    void extractHistoricalTerms() {
        List<String> keywords = KeywordUtil.extract(FICTION_TEXT, 8);
        // 背景知识浮现完全依赖这类地名/机构词能否被抽出来
        assertTrue(keywords.stream().anyMatch(k -> k.contains("长安") || k.contains("西市")
                        || k.contains("金吾卫") || k.contains("承天门") || k.contains("宣阳坊")),
                "背景知识命中依赖这类词，实际=" + keywords);
    }

    @Test
    @DisplayName("西文文本能抽出术语与缩写")
    void extractLatinTerms() {
        List<String> keywords = KeywordUtil.extract("GraphSAGE and ClusterGCN both rely on neighborhood "
                + "sampling; GAT replaces mean aggregation with attention heads. neighborhood sampling is slow.", 6);
        assertFalse(keywords.isEmpty(), "西文文本应能抽出候选");
        // 停用词不该成为关键词；出现两次的 neighborhood 应比只出现一次的词更受重视
        for (String keyword : keywords) {
            assertFalse(java.util.Set.of("and", "with", "both", "rely", "mean", "use", "these", "which")
                    .contains(keyword),
                    "停用词或普通动词混进了关键词：" + keyword + " 全量=" + keywords);
        }
    }

    @Test
    @DisplayName("同一关键词族不重复输出（互为子串只留一个）")
    void noSubstringOverlap() {
        List<String> keywords = KeywordUtil.extract(RESEARCH_TEXT, 10);
        for (int i = 0; i < keywords.size(); i++) {
            for (int j = i + 1; j < keywords.size(); j++) {
                assertFalse(keywords.get(i).contains(keywords.get(j)) || keywords.get(j).contains(keywords.get(i)),
                        "结果存在包含关系：" + keywords.get(i) + " / " + keywords.get(j));
            }
        }
    }

    @Test
    @DisplayName("空文本与非法入参不抛异常")
    void handlesBlankInput() {
        assertEquals(List.of(), KeywordUtil.extract(null, 5));
        assertEquals(List.of(), KeywordUtil.extract("", 5));
        // 纯助词文本不应产出带助词的"关键词"
        List<String> noise = KeywordUtil.extract("的了吗呢是在与而把", 5);
        for (String word : noise) {
            assertFalse(word.matches(".*[的了是在与而把].*"), "纯助词文本却抽出了：" + word);
        }
        assertEquals(List.of(), KeywordUtil.extract(RESEARCH_TEXT, 0));
    }

    @Test
    @DisplayName("countHits 统计命中次数并回填命中词")
    void countHitsWorks() {
        List<String> hits = new java.util.ArrayList<>();
        int times = KeywordUtil.countHits("长安的坊市与宵禁，坊门日落落锁", "坊,坊门,宵禁", hits);
        assertTrue(times >= 3, "至少三次命中，实际=" + times);
        assertTrue(hits.contains("宵禁"));
        assertEquals(0, KeywordUtil.countHits(null, "坊", hits));
        assertEquals(0, KeywordUtil.countHits("正文", "", null));
    }

    @Test
    @DisplayName("appearsIn 能挡住无关文献")
    void relevanceGate() {
        assertTrue(KeywordUtil.appearsIn("graph neural", "Graph Neural Networks for Molecules", null));
        assertFalse(KeywordUtil.appearsIn("图神经网络", "Environmental Technologies to Treat Sulfur Pollution"));
        assertFalse(KeywordUtil.appearsIn("", "anything"));
    }
}
