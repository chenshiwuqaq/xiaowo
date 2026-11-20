package org.example.mynote.service;

import org.example.mynote.common.PageResult;
import org.example.mynote.dto.MarketCommentSaveDTO;
import org.example.mynote.dto.MarketPluginSaveDTO;
import org.example.mynote.vo.MarketCommentVO;
import org.example.mynote.vo.MarketLikeVO;
import org.example.mynote.vo.MarketPluginDetailVO;
import org.example.mynote.vo.MarketPluginVO;
import org.example.mynote.vo.MarketRankingsVO;
import org.example.mynote.vo.MarketUseVO;

import java.util.List;

/**
 * 插件市场服务。
 *
 * <p>市场条目是插件 manifest 的快照，跨账号可见；点赞 / 评论 / 使用均按用户去重，
 * 计数反规范化到条目上供排行榜排序。</p>
 *
 * @author MyNote
 */
public interface MarketService {

    /**
     * 上架条目检索（关键词 + 分类 + 排序 + 分页）。
     *
     * @param userId   当前用户，用于回显 liked / used 状态
     * @param keyword  关键词，空串表示不过滤
     * @param category 分类，-1 表示全部
     * @param sort     排序：latest / like / comment / use
     * @param pageNo   页码，从 1 开始
     * @param pageSize 每页条数
     * @return 分页结果
     */
    PageResult<MarketPluginVO> list(Long userId, String keyword, Integer category, String sort, int pageNo, int pageSize);

    /**
     * 三张排行榜：爱心最多 / 评论最多 / 使用最多。
     *
     * @param userId 当前用户
     * @param size   每张榜单取前 N 条
     * @return 排行榜
     */
    MarketRankingsVO rankings(Long userId, int size);

    /**
     * 随机换一批（受关键词与分类约束）。
     *
     * @param userId   当前用户
     * @param keyword  关键词
     * @param category 分类，-1 表示全部
     * @param size     返回条数
     * @return 随机条目列表
     */
    List<MarketPluginVO> random(Long userId, String keyword, Integer category, int size);

    /**
     * 条目详情（含 manifest 全文与评论）。
     *
     * @param userId 当前用户
     * @param id     条目 id
     * @return 详情
     */
    MarketPluginDetailVO detail(Long userId, Long id);

    /**
     * 发布（新增上架条目）。
     */
    MarketPluginVO publish(Long userId, MarketPluginSaveDTO dto);

    /**
     * 更新自己发布的条目。
     */
    MarketPluginVO update(Long userId, Long id, MarketPluginSaveDTO dto);

    /**
     * 删除自己发布的条目（连带清理点赞/评论/使用明细）。
     */
    void delete(Long userId, Long id);

    /**
     * 上架 / 下架自己的条目。
     */
    MarketPluginVO setStatus(Long userId, Long id, boolean published);

    /**
     * 点赞 / 取消点赞（切换）。
     */
    MarketLikeVO like(Long userId, Long id);

    /**
     * 下载使用：把 manifest 装进当前用户的插件（不覆盖已有同标识插件），并去重累计使用数。
     */
    MarketUseVO use(Long userId, Long id);

    /**
     * 发表评论。
     */
    MarketCommentVO comment(Long userId, Long id, MarketCommentSaveDTO dto);

    /**
     * 删除评论（评论作者或条目作者可删）。
     */
    void deleteComment(Long userId, Long commentId);

    /**
     * 我的发布（含下架），供管理页使用。
     */
    List<MarketPluginVO> mine(Long userId);
}
