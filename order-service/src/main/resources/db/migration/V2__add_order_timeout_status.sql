-- V2: 订单状态新增「3 超时关闭」语义
-- 背景：集成 RabbitMQ 死信延时队列实现"下单后 30 分钟未支付自动关单"。
--       原状态仅有 1已创建 / 2已取消，无法区分"用户主动取消"与"超时自动关闭"，
--       故新增 3 表示超时关闭，便于统计与排查。
-- 说明：status 字段类型不变（仍是 TINYINT），仅更新注释以登记新状态含义。
--       新库由 schema.sql 直接建出含新注释的表；本脚本供"已存在的库"手动执行对齐。
ALTER TABLE orders
    MODIFY COLUMN status TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1已创建(待支付) 2已取消 3超时关闭';
