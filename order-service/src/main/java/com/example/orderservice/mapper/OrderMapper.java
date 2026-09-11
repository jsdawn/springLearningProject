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
                                        @Param("status") Integer status,
                                        @Param("offset") int offset,
                                        @Param("size") int size);

    /**
     * 统计总数（与分页查询使用同一组过滤条件）。
     */
    long countByCondition(@Param("orderNo") String orderNo,
                          @Param("userId") Long userId,
                          @Param("status") Integer status);

    /**
     * 深分页优化（延迟关联）：内层子查询只取主键 id（覆盖索引，不回表），
     * 外层按主键定位整行。与大偏移 LIMIT 相比，省去了"组装再丢弃"大量整行的成本。
     */
    List<OrderInfo> findPageByConditionDeferred(@Param("orderNo") String orderNo,
                                                @Param("userId") Long userId,
                                                @Param("status") Integer status,
                                                @Param("offset") int offset,
                                                @Param("size") int size);

    /**
     * 游标分页：返回 id 大于 lastId 的前 size 条记录（id 升序）。
     * 任意页翻页成本恒定，不支持跳页；lastId 传 0 表示从头开始。
     */
    List<OrderInfo> findPageByCursor(@Param("orderNo") String orderNo,
                                     @Param("userId") Long userId,
                                     @Param("status") Integer status,
                                     @Param("lastId") long lastId,
                                     @Param("size") int size);

    OrderInfo findById(Long id);

    List<OrderItem> findItemsByOrderId(Long orderId);

    List<OrderItem> findItemsByOrderIds(@Param("orderIds") List<Long> orderIds);

    int insert(OrderInfo orderInfo);

    int batchInsertItems(@Param("items") List<OrderItem> items);

    int updateStatusById(@Param("id") Long id, @Param("status") Integer status);

    /**
     * 条件更新订单状态（乐观锁语义）：仅当当前状态等于 fromStatus 时才更新为 toStatus。
     * <p>
     * 用于超时关单（1 待支付 → 3 超时关闭）：并发场景下若订单已被支付/取消，
     * 本语句影响行数为 0，调用方据此幂等跳过，避免误关与重复回补库存。
     *
     * @return 影响行数（1=更新成功，0=状态已变化无需处理）
     */
    int updateStatusFromTo(@Param("id") Long id,
                           @Param("fromStatus") Integer fromStatus,
                           @Param("toStatus") Integer toStatus);
}
