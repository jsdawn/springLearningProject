-- ============================================================
-- P3 EXPLAIN 对比用造数脚本（可选，压测前后对比前执行一次）
-- 前提：MySQL 8.0+（WITH RECURSIVE）
-- 说明：只 INSERT 新数据，不修改/删除现有订单与商品；
--       数据带固定前缀，清理脚本见同目录 p3-seed-cleanup.sql
-- ============================================================
USE mall_db;

-- 递归 CTE 默认深度上限 1000，造 1 万行必须先调大
SET SESSION cte_max_recursion_depth = 1000000;

-- 1. orders 造 10000 行：user_id 1~50 轮转，status 1~3 轮转
INSERT INTO orders (order_no, user_id, total_amount, status)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 10000
)
SELECT CONCAT('ORDSEED', LPAD(seq.n, 8, '0')),
       1 + (seq.n % 50),
       ROUND(10 + (seq.n % 500) + ((seq.n % 4) * 0.25), 2),
       1 + (seq.n % 3)
FROM seq;

-- 2. products 造 200 行：category_id 1~10 轮转
INSERT INTO products (category_id, product_name, price, stock, status, remark)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 200
)
SELECT 1 + (seq.n % 10),
       CONCAT('测试商品', seq.n),
       ROUND(9.9 + seq.n, 2),
       100,
       1,
       'seed'
FROM seq;
