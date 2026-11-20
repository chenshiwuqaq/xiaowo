package org.example.mynote.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 批量导入结果视图对象。
 *
 * <p>导入是"尽力而为"的批处理：单个文件解析失败只计入 failed，不中断整批，
 * titles 记录成功导入的笔记标题，供前端提示"导入了哪些"。</p>
 *
 * @author MyNote
 */
@Data
public class ImportResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 成功篇数
     */
    private Integer success = 0;

    /**
     * 失败篇数
     */
    private Integer failed = 0;

    /**
     * 成功导入的笔记标题
     */
    private List<String> titles = new ArrayList<>();
}
