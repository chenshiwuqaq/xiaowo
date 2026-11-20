package org.example.mynote.controller;

import jakarta.validation.Valid;
import org.example.mynote.common.PageResult;
import org.example.mynote.common.Result;
import org.example.mynote.common.UserContext;
import org.example.mynote.dto.MarketCommentSaveDTO;
import org.example.mynote.dto.MarketPluginSaveDTO;
import org.example.mynote.service.MarketService;
import org.example.mynote.vo.MarketCommentVO;
import org.example.mynote.vo.MarketLikeVO;
import org.example.mynote.vo.MarketPluginDetailVO;
import org.example.mynote.vo.MarketPluginVO;
import org.example.mynote.vo.MarketRankingsVO;
import org.example.mynote.vo.MarketUseVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 插件市场接口。
 *
 * <p>市场条目是用户插件的公开快照，跨账号可见；当前用户由拦截器写入的 {@link UserContext} 决定，
 * 「改谁的发布」不体现在请求参数里。</p>
 *
 * @author MyNote
 */
@RestController
@RequestMapping("/api/market")
public class MarketController {

    private final MarketService marketService;

    public MarketController(MarketService marketService) {
        this.marketService = marketService;
    }

    /**
     * 上架条目检索（关键词 + 分类 + 排序 + 分页）。
     */
    @GetMapping("/list")
    public Result<PageResult<MarketPluginVO>> list(@RequestParam(defaultValue = "") String keyword,
                                                   @RequestParam(defaultValue = "-1") Integer category,
                                                   @RequestParam(defaultValue = "latest") String sort,
                                                   @RequestParam(defaultValue = "1") int pageNo,
                                                   @RequestParam(defaultValue = "12") int pageSize) {
        return Result.ok(marketService.list(UserContext.require(), keyword, category, sort, pageNo, pageSize));
    }

    /**
     * 三张排行榜：爱心最多 / 评论最多 / 使用最多。
     */
    @GetMapping("/rankings")
    public Result<MarketRankingsVO> rankings(@RequestParam(defaultValue = "10") int size) {
        return Result.ok(marketService.rankings(UserContext.require(), size));
    }

    /**
     * 随机换一批。
     */
    @GetMapping("/random")
    public Result<List<MarketPluginVO>> random(@RequestParam(defaultValue = "") String keyword,
                                               @RequestParam(defaultValue = "-1") Integer category,
                                               @RequestParam(defaultValue = "12") int size) {
        return Result.ok(marketService.random(UserContext.require(), keyword, category, size));
    }

    /**
     * 我的发布（含下架），供管理页使用。
     */
    @GetMapping("/mine")
    public Result<List<MarketPluginVO>> mine() {
        return Result.ok(marketService.mine(UserContext.require()));
    }

    /**
     * 条目详情（含 manifest 全文与评论）。
     */
    @GetMapping("/{id}")
    public Result<MarketPluginDetailVO> detail(@PathVariable Long id) {
        return Result.ok(marketService.detail(UserContext.require(), id));
    }

    /**
     * 发布新条目。
     */
    @PostMapping
    public Result<MarketPluginVO> publish(@Valid @RequestBody MarketPluginSaveDTO dto) {
        return Result.ok(marketService.publish(UserContext.require(), dto));
    }

    /**
     * 更新自己的条目。
     */
    @PutMapping("/{id}")
    public Result<MarketPluginVO> update(@PathVariable Long id, @Valid @RequestBody MarketPluginSaveDTO dto) {
        return Result.ok(marketService.update(UserContext.require(), id, dto));
    }

    /**
     * 删除自己的条目。
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        marketService.delete(UserContext.require(), id);
        return Result.ok();
    }

    /**
     * 上架 / 下架自己的条目。
     */
    @PutMapping("/{id}/status")
    public Result<MarketPluginVO> setStatus(@PathVariable Long id, @RequestParam boolean published) {
        return Result.ok(marketService.setStatus(UserContext.require(), id, published));
    }

    /**
     * 点赞 / 取消点赞。
     */
    @PostMapping("/{id}/like")
    public Result<MarketLikeVO> like(@PathVariable Long id) {
        return Result.ok(marketService.like(UserContext.require(), id));
    }

    /**
     * 下载使用（装进我的插件并累计使用数）。
     */
    @PostMapping("/{id}/use")
    public Result<MarketUseVO> use(@PathVariable Long id) {
        return Result.ok(marketService.use(UserContext.require(), id));
    }

    /**
     * 发表评论。
     */
    @PostMapping("/{id}/comments")
    public Result<MarketCommentVO> comment(@PathVariable Long id, @Valid @RequestBody MarketCommentSaveDTO dto) {
        return Result.ok(marketService.comment(UserContext.require(), id, dto));
    }

    /**
     * 删除评论（评论作者或条目作者）。
     */
    @DeleteMapping("/comments/{commentId}")
    public Result<Void> deleteComment(@PathVariable Long commentId) {
        marketService.deleteComment(UserContext.require(), commentId);
        return Result.ok();
    }
}
