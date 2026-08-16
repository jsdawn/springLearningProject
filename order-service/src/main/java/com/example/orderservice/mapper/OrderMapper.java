package com.example.orderservice.mapper;

import com.example.orderservice.entity.OrderInfo;
import com.example.orderservice.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrderMapper {

    List<OrderInfo> findAll(@Param("orderNo") String orderNo, @Param("userId") Long userId);

    /**
     * 分页查询（带可选过滤条件）。
     */
    List<OrderInfo> findPageByCondition(@Param("orderNo") String orderNo,
                                        @Param("userId") Long userId,
                                        @Param("offset") int offset,
                                        @Param("size") int size);

    /**
     * 统计总数（与分页查询使用同一组过滤条件）。
     */
    long countByCondition(@Param("orderNo") String orderNo, @Param("userId") Long userId);

    OrderInfo findById(Long id);

    List<OrderItem> findItemsByOrderId(Long orderId);

    List<OrderItem> findItemsByOrderIds(@Param("orderIds") List<Long> orderIds);

    int insert(OrderInfo orderInfo);

    int batchInsertItems(@Param("items") List<OrderItem> items);

    int updateStatusById(@Param("id") Long id, @Param("status") Integer status);
}
