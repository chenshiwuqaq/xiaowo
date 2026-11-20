package org.example.mynote.common;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页结果封装。
 *
 * @param <T> 列表元素类型
 * @author MyNote
 */
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 当前页数据
     */
    private List<T> list;

    /**
     * 总记录数
     */
    private long total;

    /**
     * 当前页码，从 1 开始
     */
    private int pageNo;

    /**
     * 每页条数
     */
    private int pageSize;

    public PageResult() {
    }

    public PageResult(List<T> list, long total, int pageNo, int pageSize) {
        this.list = list;
        this.total = total;
        this.pageNo = pageNo;
        this.pageSize = pageSize;
    }

    /**
     * 构造空分页结果。
     *
     * @param pageNo   页码
     * @param pageSize 每页条数
     * @param <T>      元素类型
     * @return 空结果
     */
    public static <T> PageResult<T> empty(int pageNo, int pageSize) {
        return new PageResult<>(Collections.emptyList(), 0L, pageNo, pageSize);
    }

    public List<T> getList() {
        return list;
    }

    public void setList(List<T> list) {
        this.list = list;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getPageNo() {
        return pageNo;
    }

    public void setPageNo(int pageNo) {
        this.pageNo = pageNo;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}
