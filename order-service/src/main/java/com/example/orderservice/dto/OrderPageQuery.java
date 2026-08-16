package com.example.orderservice.dto;

/**
 * 订单分页查询入参（GET QueryString）。
 *
 * pageNum/pageSize 为统一分页入参；orderNo 支持模糊查询，userId 为精确匹配，可按需扩展更多条件字段。
 */
public class OrderPageQuery {

    /**
     * 页码，默认 1
     */
    private Integer pageNum = 1;

    /**
     * 每页条数，默认 10
     */
    private Integer pageSize = 10;

    /**
     * 订单号（模糊）
     */
    private String orderNo;

    /**
     * 用户 ID（精确）
     */
    private Long userId;

    public Integer getPageNum() {
        return pageNum;
    }

    public void setPageNum(Integer pageNum) {
        this.pageNum = pageNum;
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
}
