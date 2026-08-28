# RabbitMQ 死信延时关单集成说明（下单 30 分钟未支付自动关闭 + 回补库存）

适用于本项目：SpringBoot 2.7.18 + Spring Cloud 2021.0.5 + JDK 1.8，仅 `order-service` 接入。

## 1. 集成概览

| 模块 | 接入内容 |
| --- | --- |
| 父 pom | 无改动，`spring-boot-starter-amqp` 版本由 `spring-boot-starter-parent` 2.7.18 托管 |
| order-service | amqp starter + 死信延时拓扑配置 + 延时消息生产者 + 死信消费者 + 调试接口 |
| dev-env-docker | 新增 `rabbitmq:3.12-management` 容器（5672 / 管理台 15672） |
| 数据库 | `orders.status` 新增状态 `3=超时关闭`（schema.sql 与 V2 变更脚本） |

业务目标：**下单成功后 30 分钟未支付，自动关闭订单（状态 1→3）并把库存回补给商品服务**。

## 2. 实现原理：TTL + 死信队列（DLX）

RabbitMQ 原生不支持"定时触发"，本方案用"消息过期自动转发"模拟延时：

```
生产者 ──order.delay.key──▶ order.delay.exchange (direct)
      ──▶ order.delay.queue（延时队列：无消费者，x-message-ttl=30分钟）
            │  消息待满 30 分钟后过期
            ▼  按 x-dead-letter-exchange / x-dead-letter-routing-key 转发
      order.dlx.exchange (direct) ──order.dlx.key──▶ order.dlx.queue（死信队列）
            ──▶ OrderDeadLetterConsumer 消费：仍待支付则关单 + 回补库存
```

队列关键参数（`RabbitMqConfig`）：

| 参数 | 值 | 作用 |
| --- | --- | --- |
| `x-message-ttl` | `order.mq.delay-ttl-ms`（默认 1800000=30 分钟） | 延时队列消息存活上限，到期即过期 |
| `x-dead-letter-exchange` | `order.dlx.exchange` | 过期消息转发目标交换机 |
| `x-dead-letter-routing-key` | `order.dlx.key` | 转发时使用的路由键 |

> 注意：队列参数不可变。若曾在管理台手动建过同名但参数不同的队列，启动会报
> `PRECONDITION_FAILED`，先在管理台删除旧队列再启动。

## 3. 防消息丢失三道防线

| 环节 | 配置/代码 | 说明 |
| --- | --- | --- |
| 生产端确认 | `publisher-confirm-type: correlated` + `RabbitTemplate.setConfirmCallback` | 消息是否到达 Broker，ack/nack 异步回调，日志含订单号 |
| 路由退回 | `publisher-returns: true` + `setMandatory(true)` + `setReturnsCallback` | 消息到 Broker 但路由不到队列时退回并告警 |
| 消费端确认 | `acknowledge-mode: manual` + `channel.basicAck/basicNack` | 业务处理完才确认；异常 `basicNack(requeue=true)` 重投；毒消息 ACK 丢弃 |

> 自定义 `RabbitTemplate` Bean 后，Boot 对 RabbitTemplate 的自动配置整体让位，
> 因此 `mandatory` 在代码里显式设置（yml 的 `spring.rabbitmq.template.*` 不再自动生效）。

## 4. 变更文件清单

| 文件 | 动作 |
| --- | --- |
| `order-service/pom.xml` | 新增 `spring-boot-starter-amqp` |
| `order-service/src/main/resources/application.yml` | RabbitMQ 连接、confirm/returns、手动 ACK、prefetch、`order.mq.delay-ttl-ms` |
| `config/RabbitMqConfig.java` | 拓扑声明（2 交换机 + 2 队列 + 2 绑定）+ JSON 转换器 + RabbitTemplate 回调 |
| `mq/OrderDelayMessage.java` | 延时消息体 DTO（orderId/orderNo/userId/createTimeMillis） |
| `mq/OrderDelayMessageProducer.java` | 发送延时消息，支持自定义 TTL |
| `mq/OrderDeadLetterConsumer.java` | 死信消费者：幂等判断 + 手动 ACK |
| `controller/OrderController.java` | `create()` 下单成功后追加发送延时消息（原下单业务未动） |
| `service/OrderService.java`、`OrderServiceImpl.java` | 追加 `closeExpiredOrder(id)`：条件更新 1→3 + 回补库存 |
| `mapper/OrderMapper.java`、`OrderMapper.xml` | 追加 `updateStatusFromTo`（乐观锁条件更新） |
| `controller/OrderMqTestController.java` | 调试接口（短 TTL 快速验证） |
| `resources/schema.sql`、`db/migration/V2__add_order_timeout_status.sql` | 状态注释新增 `3超时关闭` |
| `dev-env-docker/docker-compose.yml`、`.env.example` | 新增 RabbitMQ 服务 |

## 5. 订单状态机

| 状态值 | 含义 | 进入方式 |
| --- | --- | --- |
| 1 | 已创建（待支付） | 下单成功 |
| 2 | 已取消 | 用户主动取消 `PATCH /orders/{id}/cancel` |
| 3 | 超时关闭 | 死信消费者自动关闭（本次新增） |

消费者只在状态为 1 时关单；状态 2/3 或订单不存在一律幂等跳过，重复消息无副作用。

## 6. 本地启动与调试步骤

前置：`dev-env-docker` 里 `docker compose up -d`（会一并拉起 rabbitmq），
管理台 http://localhost:15672（guest/guest）。

1. 启动 `product-service`、`user-service`、`order-service`（Nacos/MySQL 正常）。
2. 正常下单：

   ```bash
   curl -X POST http://localhost:8083/orders \
     -H "Content-Type: application/json" \
     -d '{"userId":1,"items":[{"productId":1,"quantity":2}]}'
   ```

   返回订单（`status=1`），管理台中 `order.delay.queue` 出现 1 条消息。
3. **快速验证（不必等 30 分钟）**：对该订单发一条 15 秒延时消息：

   ```bash
   curl -X POST "http://localhost:8083/order-mq-test/delay/{orderId}?ttlSeconds=15"
   ```

   原理：队列 TTL 与消息 TTL 并存时取较短者，15 秒即过期进入死信队列。
4. 约 15 秒后查询订单：`GET /orders/{orderId}`，`status` 变为 `3`；
   同时 `GET product-service /products/{productId}` 库存已加回。
5. 验证幂等跳过分支：

   ```bash
   curl -X POST "http://localhost:8083/order-mq-test/delay-mock?orderId=999999&ttlSeconds=10"
   ```

   order-service 日志出现 `[MQ-DLQ] 订单无需关闭（不存在或非待支付状态），幂等跳过`。

日志关键字速查：`[MQ-Send]` 发送、`[MQ-Confirm]` 确认、`[MQ-Return]` 退回、`[MQ-DLQ]` 消费。

## 7. 已知边界与生产化方向（本项目未实现，仅说明）

| 场景 | 当前处理 | 生产建议 |
| --- | --- | --- |
| 下单成功但发消息失败 | 记 ERROR，订单不自动关闭 | 本地消息表 / 定时兜底扫描（status=1 且超 30 分钟） |
| 消费异常 | `basicNack(requeue=true)` 重投 | 重试次数上限 + 死信再死信归档队列，防毒消息循环 |
| 回补库存单条失败 | 记 ERROR，不阻断关单 | 对账/补偿任务保证最终一致 |
| 消息堆积时的短 TTL 调试消息 | 队列头部若有长 TTL 消息，短 TTL 消息需等其先过期（RabbitMQ 仅检查队首） | 生产延时场景可用 `rabbitmq_delayed_message_exchange` 插件 |
