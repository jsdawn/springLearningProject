-- V3: orders 用 (user_id, status) 联合索引替换两个单列索引
-- 背景：P3 数据库专项。订单分页接口新增 status 筛选后，出现
--       "user_id + status" 组合查询路径，单列索引无法覆盖。
-- 说明：- "按用户查订单"走联合索引左前缀 user_id，单列 idx_orders_user_id 冗余
--       - status 只有 3 个取值，低基数单列索引几乎不会被优化器选中，删除
--       - 新库由 schema.sql 直接建出联合索引；本脚本供"已存在的库"手动执行对齐，只跑一次。
ALTER TABLE orders
    ADD INDEX idx_orders_user_status (user_id, status),
    DROP INDEX idx_orders_user_id,
    DROP INDEX idx_orders_status;
