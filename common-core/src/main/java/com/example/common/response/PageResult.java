package com.example.common.response;

import java.util.Collections;
import java.util.List;

/**
 * 统一分页返回对象。
 *
 * @param <T> records 中的元素类型
 */
public class PageResult<T> {

    /**
     * 当前页数据列表
     */
    private List<T> records;

    /**
     * 总记录数
     */
    private Long total;

    /**
     * 总页数
     */
    private Long pages;

    /**
     * 当前页码（从 1 开始）
     */
    private Integer current;

    /**
     * 每页条数
     */
    private Integer size;

    public static <T> PageResult<T> empty(Integer current, Integer size) {
        PageResult<T> page = new PageResult<>();
        page.setRecords(Collections.emptyList());
        page.setTotal(0L);
        page.setPages(0L);
        page.setCurrent(current);
        page.setSize(size);
        return page;
    }

    public static <T> PageResult<T> of(List<T> records, long total, int current, int size) {
        PageResult<T> page = new PageResult<>();
        page.setRecords(records);
        page.setTotal(total);
        page.setPages(calcPages(total, size));
        page.setCurrent(current);
        page.setSize(size);
        return page;
    }

    private static long calcPages(long total, int size) {
        if (size <= 0) {
            return 0L;
        }
        return (total + size - 1L) / size;
    }

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }

    public Long getPages() {
        return pages;
    }

    public void setPages(Long pages) {
        this.pages = pages;
    }

    public Integer getCurrent() {
        return current;
    }

    public void setCurrent(Integer current) {
        this.current = current;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }
}
