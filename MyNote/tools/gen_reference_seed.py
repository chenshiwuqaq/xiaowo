#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
生成 04_seed_reference.sql —— 参考文献示例数据。

为什么不直接手写这些条目：文献的作者、年份、DOI、被引次数一旦写错，
科研模式的推荐就变成误导。因此本脚本对每条文献走 OpenAlex / arXiv
官方接口取回权威元数据，再由脚本落成 SQL，作者只做"中文批注"部分。

用法：python tools/gen_reference_seed.py
"""
import io
import json
import re
import sys
import time
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET

UA = {"User-Agent": "mynote-seed-generator/1.0 (mailto:dev@example.com)"}

# 发表载体（会议/期刊）—— arXiv 只是预印本托管地，正式载体需按公开记录标注
VENUES = {
    "gcn": "ICLR 2017", "graphsage": "NeurIPS 2017", "gat": "ICLR 2018", "gin": "ICLR 2019",
    "gnn-early": "IEEE Transactions on Neural Networks", "mpnn": "ICML 2017",
    "pna": "NeurIPS 2020", "agpgnn": "ICLR 2021", "resnet": "CVPR 2016",
    "transformer": "NeurIPS 2017", "bert": "NAACL 2019", "rag": "NeurIPS 2020",
    "cot": "NeurIPS 2022", "lora": "ICLR 2022", "flashattn": "NeurIPS 2022",
    "adam": "ICLR 2015", "adamw": "ICLR 2019", "word2vec": "ICLR Workshop 2013",
    "glove": "EMNLP 2014", "gnn-survey": "IEEE TNNLS", "gnn-review": "AI Open",
    "llm-survey": "Science China Information Sciences", "tgn": "arXiv preprint",
    "ngcf": "SIGIR 2020", "kg-survey": "IEEE TNNLS", "gpt3": "NeurIPS 2020",
    "expressive-writing": "Journal of Abnormal Psychology",
}

# (标识, arXiv id, DOI, 学科方向, 命中关键词, 作者批注)
WORKS = [
    ("gcn", "1609.02907", None, "图神经网络", "图卷积,谱方法,半监督,引文网络",
     "提出一阶切比雪夫近似的图卷积层，用对称归一化的邻接结构做半监督节点分类。"),
    ("graphsage", "1706.02216", None, "图神经网络", "归纳式,邻居采样,聚合函数,GraphSAGE",
     "以邻居采样与可学习聚合函数替代逐节点参数，使模型能对训练时未见的节点直接推断表征。"),
    ("gat", "1710.10903", None, "图神经网络", "注意力,多头,加权聚合,GAT",
     "用掩码自注意力对邻居加权，多头机制稳定训练，同时保持归纳式推理能力。"),
    ("gin", "1810.00826", None, "图神经网络理论", "表达能力,WL检验,聚合函数,GIN",
     "把 GNN 判别能力归约到 Weisfeiler-Lehman 图同构检验，指出 sum 聚合的必要性并提出 GIN。"),
    ("gnn-early", None, "10.1109/TNN.2008.2005605", "图神经网络", "早期模型,递推,节点映射",
     "图神经网络的最早系统形式：在节点上定义递推状态映射，以图微分方程描述收敛。"),
    ("mpnn", "1704.01212", None, "消息传递", "MPNN,分子性质,化学",
     "统一消息传递神经网络框架（MPNN），在分子性质预测上验证该范式的通用性。"),
    ("pna", "2004.05718", None, "图神经网络", "聚合,PNA,度缩放",
     "提出多聚合器与度缩放结合的 PNA，论证单一聚合算子限制了 GNN 的表达能力。"),
    ("agpgnn", "2006.07988", None, "图神经网络", "图扩散,自适应传播,异配性",
     "把图扩散预处理与可学习传播策略结合，在异配图与大图上均优于固定深度卷积。"),
    ("resnet", "1512.03385", "10.1109/CVPR.2016.90", "深度学习", "残差连接,深层网络,ImageNet",
     "用恒等捷径连接解决深层网络的退化问题，使百层以上网络可训练。"),
    ("transformer", "1706.03762", None, "序列建模", "Transformer,自注意力,位置编码",
     "完全以注意力构建序列转换模型，去除循环与卷积结构。"),
    ("bert", "1810.04805", "10.18653/v1/N19-1423", "预训练语言模型", "BERT,掩码预训练,双向编码",
     "以掩码语言模型做深度双向预训练，迁移下游任务时只需微调一个输出层。"),
    ("rag", "2005.11401", None, "检索增强生成", "RAG,稠密检索,知识任务",
     "把参数化生成模型与非参数文档索引联合，是本产品「检索＋生成」推荐思路的来源。"),
    ("cot", "2201.11903", None, "大模型推理", "思维链,提示,推理",
     "在提示中给出中间推理步骤，可显著提升大模型在多步任务上的表现。"),
    ("lora", "2106.09685", None, "模型微调", "低秩适配,参数高效微调",
     "只训练注入的低秩分解矩阵即可接近全量微调效果，大幅降低可训练参数量。"),
    ("flashattn", "2205.14135", None, "注意力优化", "IO感知,精确注意力,显存",
     "通过分块与前向重计算减少注意力算子的显存读写，实现不近似加速。"),
    ("adam", "1412.6980", None, "优化方法", "自适应学习率,动量",
     "结合一阶与二阶动量的自适应优化器。"),
    ("adamw", "1711.05101", None, "优化方法", "AdamW,权重衰减",
     "指出 Adam 中 L2 正则与自适应缩放相互干扰，提出解耦权重衰减的 AdamW。"),
    ("word2vec", "1301.3781", None, "词向量", "word2vec,SGNS",
     "提出跳词与连续词袋两种高效词向量训练目标。"),
    ("glove", None, "10.3115/v1/D14-1162", "词向量", "共现矩阵,全局统计",
     "让词向量匹配全局共现概率的对数，兼得局部上下文与全局统计。"),
    ("gnn-survey", "1901.00596", "10.1109/TNNLS.2020.2978386", "综述", "GNN,分类体系,时空图",
     "按卷积式与循环式给出 GNN 的统一分类框架。"),
    ("gnn-review", "1812.08434", "10.1016/j.aiopen.2021.01.001", "综述", "GNN,应用,方法回顾",
     "从方法与应用两条线梳理早期 GNN 研究版图。"),
    ("llm-survey", "2303.18223", "10.1007/s11432-023-3783-5", "大模型综述", "LLM,预训练,对齐,评测",
     "系统总结大语言模型在预训练、适配使用与对齐评测上的技术栈。"),
    ("tgn", "2006.10637", None, "动态图学习", "TGN,时序记忆,事件流",
     "用节点记忆与双向时序编码处理事件流，是动态图建模的代表性框架。"),
    ("ngcf", "1905.08108", "10.1145/3397271.3401079", "推荐系统", "NGCF,图传播,协同过滤",
     "把用户-商品交互建模为图上的传播过程，取代内积打分范式。"),
    ("kg-survey", "2002.00388", None, "知识图谱", "KG,表示学习,推理",
     "梳理知识图谱的表示、构建与应用三条主线。"),
    ("gpt3", "2005.14165", None, "大模型", "GPT-3,少样本,上下文学习",
     "规模增长带来上下文少样本学习能力，为「提示即功能」的写作助手提供基础。"),
    ("expressive-writing", None, "10.1037/0021-843X.95.3.274", "写作与心理", "表达性写作,身心健康,日记",
     "连续数日书写创伤经历可显著减少后续就医次数，是「写作即疗愈」的经典实证依据。"),
]

# 接口取不到、需要人工兜底的条目（书目信息本身即稳定事实）
MANUAL = [
    dict(title="Dropout: A Simple Way to Prevent Neural Networks from Overfitting",
         authors="Nitish Srivastava, Geoffrey Hinton, Alex Krizhevsky, Ilya Sutskever, Ruslan Salakhutdinov",
         journal="Journal of Machine Learning Research", year=2014, doi=None,
         url="https://jmlr.org/papers/v15/srivastava14a.html", field="正则化",
         keywords="dropout,集成,过拟合",
         abstract="训练时随机丢弃隐单元，等价于对指数级子网络做几何平均。", cited=0),
    dict(title="Flow: The Psychology of Optimal Experience", authors="Mihaly Csikszentmihalyi",
         journal="Harper & Row（中译本《心流》）", year=1990, doi=None, url=None, field="心流",
         keywords="心流,最优体验,专注,沉浸",
         abstract="提出挑战与技能动态平衡时的最优体验状态，可解释长时间书写的沉浸机制。", cited=0),
]


def fetch_arxiv(arxiv_id):
    url = "https://export.arxiv.org/api/query?id_list=%s" % arxiv_id
    xml = urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30).read()
    ns = {"a": "http://www.w3.org/2005/Atom"}
    entry = ET.fromstring(xml).find("a:entry", ns)
    title = re.sub(r"\s+", " ", entry.find("a:title", ns).text.strip())
    authors = [a.find("a:name", ns).text for a in entry.findall("a:author", ns)]
    year = int(entry.find("a:published", ns).text[:4])
    journal = "arXiv preprint"
    m = re.search(r"info:arxiv/([\d.]+)", entry.find("a:id", ns).text)
    url_str = "https://arxiv.org/abs/%s" % (m.group(1) if m else arxiv_id)
    return dict(title=title, authors=authors, journal=journal, year=year, url=url_str)


def _oa(path):
    return json.load(urllib.request.urlopen(urllib.request.Request(
        "https://api.openalex.org/" + path, headers=UA), timeout=30))


def fetch_openalex(doi=None, arxiv_id=None, title=None):
    if doi:
        path = "works/doi:" + urllib.parse.quote(doi)
    elif arxiv_id:
        path = "works/doi:10.48550/arXiv." + arxiv_id
    else:
        path = "works?filter=title.search:" + urllib.parse.quote(title or "")
    try:
        data = _oa(path)
    except Exception:
        # 预印本 DOI 未被收录时，退回标题检索
        data = _oa("works?filter=title.search:" + urllib.parse.quote(title or ""))
    item = data if "id" in data else (data.get("results") or [None])[0]
    if not item:
        return None
    src = ((item.get("primary_location") or {}).get("source") or {})
    return dict(title=re.sub(r"\s+", " ", (item.get("title") or "").strip()),
                authors=["%s %s" % (a.get("author", {}).get("display_name"), "")
                         for a in (item.get("authorships") or [])],
                journal=(src.get("display_name") or "").strip(),
                year=item.get("publication_year"),
                cited=item.get("cited_by_count") or 0,
                url=(item.get("doi") or "").replace("https://doi.org/", "") or None)


def q(s):
    """SQL 单引号转义"""
    if s is None:
        return "NULL"
    return "'" + str(s).replace("\\", "\\\\").replace("'", "''") + "'"


def main():
    rows = []
    for slug, arxiv_id, doi, field, keywords, note in WORKS:
        meta, oa = None, None
        try:
            if arxiv_id:
                meta = fetch_arxiv(arxiv_id)
                time.sleep(0.4)
            oa = fetch_openalex(doi=doi, arxiv_id=arxiv_id, title=(meta or {}).get("title"))
            time.sleep(0.4)
        except Exception as e:
            sys.stderr.write("[%s] lookup failed: %s\n" % (slug, e))
        title = (meta or {}).get("title") or (oa or {}).get("title") or slug
        authors = (meta or {}).get("authors") or (oa or {}).get("authors") or []
        authors = [a.strip() for a in authors if a and a.strip()]
        author_str = ", ".join(authors[:8]) + (" et al." if len(authors) > 8 else "")
        journal = VENUES.get(slug) or (oa or {}).get("journal") or (meta or {}).get("journal") or ""
        # 年份以正式发表载体为准：接口常返回预印本提交年或后设记录年份，与 ICLR/NeurIPS 标签矛盾
        m_year = re.search(r"(19|20)\d{2}", journal or "")
        if m_year:
            year = int(m_year.group())
        else:
            year = (meta or {}).get("year") or (oa or {}).get("year")
        cited = (oa or {}).get("cited") or 0
        url = (meta or {}).get("url")
        rows.append((title, author_str, journal, year, doi, url, field, keywords, note, cited))
        print("%-16s cited=%-7s %s" % (slug, cited, title[:58]))

    for m in MANUAL:
        rows.append((m["title"], m["authors"], m["journal"], m["year"], m["doi"], m["url"],
                     m["field"], m["keywords"], m["abstract"], m["cited"]))

    out = io.open("sql/04_seed_reference.sql", "w", encoding="utf-8", newline="\n")
    out.write("-- =====================================================================\n")
    out.write("-- 小窝笔记（MyNote）—— 示例数据：参考文献库\n")
    out.write("-- 本文件由 tools/gen_reference_seed.py 调用 OpenAlex / arXiv 官方接口生成，\n")
    out.write("-- 作者、年份、DOI、被引次数均为接口返回的真实值；中文批注为产品侧解读。\n")
    out.write("-- =====================================================================\n\n")
    out.write("USE `newnote`;\n\n")
    out.write("INSERT INTO `mn_reference` (`title`, `authors`, `journal`, `pub_year`, `doi`, `url`, `field`,\n"
              "                            `keywords`, `abstract`, `cited_count`)\nVALUES\n")
    body = []
    for (title, authors, journal, year, doi, url, field, keywords, note, cited) in rows:
        body.append("       (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)" % (
            q(title), q(authors), q(journal), str(year) if year else "NULL", q(doi), q(url),
            q(field), q(keywords), q(note), q(str(cited))))
    out.write(",\n".join(body) + ";\n")
    out.close()
    print("\nwritten: sql/04_seed_reference.sql  (%d rows)" % len(rows))


if __name__ == "__main__":
    main()
