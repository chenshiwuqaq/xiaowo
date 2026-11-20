package org.example.mynote.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 笔记历史版本列表项视图对象。
 *
 * <p>不携带正文：历史列表往往一次拉出几十条 LONGTEXT 快照，只展示"何时存了哪个版本"
 * 即可，正文留到 restore 时再按需取，避免列表接口体积失控。</p>
 *
 * @author MyNote
 */
@Data
public class NoteHistoryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 版本 id
     */
    private Long id;

    /**
     * 快照时的标题
     */
    private String title;

    /**
     * 快照时的字数
     */
    private Integer wordCount;

    /**
     * 来源：save 手动保存 / ai AI 覆盖前 / import 导入
     */
    private String source;

    /**
     * 快照时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
