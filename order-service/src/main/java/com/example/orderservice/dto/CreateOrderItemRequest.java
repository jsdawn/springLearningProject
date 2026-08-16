package com.example.orderservice.dto;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * 创建订单明细请求
 */
public class CreateOrderItemRequest {

    /**
     * 商品 ID
     */
    @NotNull(message = "商品 ID 不能为空")
    private Long productId;

    /**
     * 购买数量
     */
    @NotNull(message = "购买数量不能为空")
    @Min(value = 1, message = "购买数量最小为 1")
    private Integer quantity;

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
