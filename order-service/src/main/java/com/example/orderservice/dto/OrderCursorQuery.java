package com.example.orderservice.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/**
 * 订单游标分页查询入参（GET QueryString）。
 *
 * <p>与 {@link OrderPageQuery} 的区别：不使用页码，而是以上一页最后一条记录的 id
 * 作为游标（lastId）续拉下一页。翻页成本与页深无关，适合"加载更多"式交互；
 * 代价是不支持跳页，也不返回总数。
 */
public class OrderCursorQuery {

    /**
     * 游标：上一页最后一条记录的 id，默认 0 表示从第一页开始
     */
    @Min(value = 0, message = "游标 lastId 最小为 0")
    private Long lastId = 0L;

    /**
     * 每页条数，默认 10
     */
    @Min(value = 1, message = "每页条数最小为 1")
    @Max(value = 100, message = "每页条数最大为 100")
    private Integer pageSize = 10;

    /**
     * 订单号（模糊）
     */
    private String orderNo;

    /**
     * 用户 ID（精确）
     */
    @Min(value = 1, message = "用户 ID 最小为 1")
    private Long userId;

    /**
     * 订单状态（精确）：1 已创建（待支付）2 已取消 3 超时关闭，不传查全部
     */
    @Min(value = 1, message = "订单状态最小为 1")
    @Max(value = 3, message = "订单状态最大为 3")
    private Integer status;

    public Long getLastId() {
        return lastId;
    }

    public void setLastId(Long lastId) {
        this.lastId = lastId;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
