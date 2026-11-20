package org.example.mynote.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 字数趋势折线图数据。
 *
 * <p>total 与 max 由后端顺手算出：前端画柱子时需要一个纵轴上界，
 * 若让它自己遍历，窗口一改（7/30/90 天）就容易和后端口径不一致。</p>
 *
 * <p>max 是窗口内单日最高净增字数，并且至少为 0——全是 0 的窗口若把上界交给前端，
 * 折线图会按 0 或 NaN 画，这里给出确定的下限。</p>
 *
 * @author MyNote
 */
@Data
public class TrendVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 按日期正序的每日字数，缺失日期已补 0
     */
    private List<DailyWordVO> days;

    /**
     * 窗口内合计净增字数（含删改的负值，因此理论上可能为负）
     */
    private Long total;

    /**
     * 窗口内单日最高字数，用作图表纵轴上界，不小于 0
     */
    private Integer max;
}
