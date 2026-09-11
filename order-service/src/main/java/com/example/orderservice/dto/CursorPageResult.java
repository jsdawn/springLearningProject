package com.example.orderservice.dto;

import java.util.Collections;
import java.util.List;

/**
 * 游标分页返回对象。
 *
 * <p>与 {@code PageResult} 的区别：不返回 total/pages（游标模式下计数本身可能比翻页更贵，
 * 且持续写入的数据总数随时在变，跳页语义也不成立），改为返回游标续拉信息：
 * <ul>
 *   <li>{@code nextCursor}：本页最后一条记录的 id，作为下一页请求的 lastId 传入；
 *       为 null 表示后面没有数据了
 *   <li>{@code hasMore}：是否还有下一页（服务端多查一条探测得出）
 * </ul>
 *
 * @param <T> records 中的元素类型
 */
public class CursorPageResult<T> {

    /**
     * 当前页数据列表
     */
    private List<T> records;

    /**
     * 下一页游标（本页最后一条记录的 id）；null 表示没有更多数据
     */
    private Long nextCursor;

    /**
     * 是否还有下一页
     */
    private Boolean hasMore;

    public CursorPageResult() {
    }

    public CursorPageResult(List<T> records, Long nextCursor, Boolean hasMore) {
        this.records = records;
        this.nextCursor = nextCursor;
        this.hasMore = hasMore;
    }

    public static <T> CursorPageResult<T> empty() {
        return new CursorPageResult<>(Collections.emptyList(), null, false);
    }

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records;
    }

    public Long getNextCursor() {
        return nextCursor;
    }

    public void setNextCursor(Long nextCursor) {
        this.nextCursor = nextCursor;
    }

    public Boolean getHasMore() {
        return hasMore;
    }

    public void setHasMore(Boolean hasMore) {
        this.hasMore = hasMore;
    }
}
