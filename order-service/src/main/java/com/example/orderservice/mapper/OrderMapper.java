package com.example.orderservice.mapper;

import com.example.orderservice.entity.OrderInfo;
import com.example.orderservice.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrderMapper {

    List<OrderInfo> findAll(@Param("orderNo") String orderNo, @Param("userId") Long userId);

    OrderInfo findById(Long id);

    List<OrderItem> findItemsByOrderId(Long orderId);

    int insert(OrderInfo orderInfo);

    int batchInsertItems(@Param("items") List<OrderItem> items);

    int updateStatusById(@Param("id") Long id, @Param("status") Integer status);
}
