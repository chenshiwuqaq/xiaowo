package org.example.mynote.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 书写热力图数据。
 *
 * <p>外面包一层 days 而不是直接返回数组，是为了与 /trend 保持同一种响应形状，
 * 前端两个图能复用同一套渲染代码；将来要加"总计""起始日"等元信息也不用改接口结构。</p>
 *
 * @author MyNote
 */
@Data
public class WritingCalendarVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 按日期正序排列的每日字数，缺失日期已补 0，格子数固定等于请求的 days
     */
    private List<DailyWordVO> days;
}
