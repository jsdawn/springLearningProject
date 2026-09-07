# P1 Seata AT 分布式事务接入

## 目标

解决下单链路的跨服务数据一致性问题：`order-service` 写订单 + `product-service` 扣库存，两步必须同时成功或同时回滚。在此之前，扣库存成功后若订单写库失败，只能靠业务代码"尽力而为"地反向调接口回补，既不可靠又难维护。

## 现状摸底（开始前）

- **Seata Server 已就绪**：`dev-env-docker/docker-compose.yml` 部署 `seataio/seata-server:1.6.1`，注册到 Nacos（`application=seata-server`、`group=SEATA_GROUP`、`cluster=default`），配置中心用 `file`
- **undo_log 已建表**：语句放在 `user-service/src/main/resources/schema.sql`（三服务共用 `mall_db`，只需一份）
- **业务服务是白纸**：`order / product / user` 三个服务的 pom 与 yml 此前完全没有 Seata 相关配置
- **待替代的临时方案**：`OrderServiceImpl.createOrder` 的 catch 块里调用 `rollbackAdjustedStock` 做库存回补，注释明确写着"当前阶段还没有分布式事务"

## 版本对齐

| 组件                                   | 版本         | 来源                                      |
| ------------------------------------ | ---------- | --------------------------------------- |
| Seata Server（TC）                     | 1.6.1      | `dev-env-docker/.env` 的 `SEATA_VERSION` |
| `spring-cloud-starter-alibaba-seata` | 2021.0.5.0 | 父工程 SCA BOM 托管                          |
| `seata-spring-boot-starter`          | 1.6.1      | 由上述 starter 传递引入                        |

关键点：starter 的版本号跟的是 **SCA 版本（2021.0.5.0）** 而不是 Seata 版本（1.6.1），但它内部依赖的 `seata-spring-boot-starter` 是 1.6.1，与 Server 一致。所以**子模块引入时不要写 `<version>`**，交给 BOM 管理，否则容易把两边版本拉岔。

## 实际改动

### 1. 两个服务加依赖

`order-service/pom.xml` 与 `product-service/pom.xml` 各加一段（不写版本号）：

```xml
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-seata</artifactId>
</dependency>
```

`user-service` **不加**：下单链路里它只 `getUserById` 查数据、不改数据，没有分支事务可回滚，加了只是徒增依赖和启动耗时。

### 2. 两个服务加 yml 配置

两边配置完全一致：

```yaml
seata:
  enabled: true
  tx-service-group: mall_tx_group
  enable-auto-data-source-proxy: true   # AT 核心，见下方设计要点
  registry:
    type: nacos
    nacos:
      application: seata-server
      server-addr: ${NACOS_SERVER_ADDR:localhost:8848}
      namespace: ${NACOS_NAMESPACE:}
      group: SEATA_GROUP
      cluster: default
  service:
    vgroup-mapping:
      mall_tx_group: default
```

参数必须与 `dev-env-docker/seata/config/application.yml` 的 registry 段对得上，否则客户端找不到 TC（启动报 `no available service`）。

### 3. `createOrder` 改造为全局事务

```java
@Override
@GlobalTransactional(name = "create-order", rollbackFor = Exception.class)
@Transactional
public OrderInfo createOrder(CreateOrderRequest request) {
    // ... 校验、鉴权、扣库存、写订单主表与明细
}
```

同时**删掉了两处东西**：

- catch 块里的 `rollbackAdjustedStock(deductedItems)` 调用 —— 反向补偿改由 Seata 依据 undo_log 自动完成
- `rollbackAdjustedStock` 方法本身与 `deductedItems` 变量

原来是 `try { ... } catch (RuntimeException e) { 尽力回补; throw e; }`，现在是平铺的直线代码，异常直接往上抛，由 Seata 的切面接管回滚。这是本次改造最能体现价值的地方：**业务代码不再需要为"跨服务回滚"写任何补偿逻辑**。

## 验证清单（Apifox）

先确认基础环境：Nacos 控制台能看到 `seata-server` 服务，Seata 控制台 `http://localhost:7091`（seata/seata）可登录。

| 场景          | 步骤                                 | 期望                                                                      |
| ----------- | ---------------------------------- | ----------------------------------------------------------------------- |
| 正常下单        | 登录后 POST `/orders`                 | 200，订单入库，商品 stock 减少对应数量                                                |
| 全局回滚        | 见下方「故障注入」，下单含 2 个商品                | 接口 500；**两个商品的 stock 都回到原值**（含第一个已扣减的）                                  |
| undo_log 落库 | 故障注入期间立刻查 `SELECT * FROM undo_log` | 回滚过程中能看到分支记录，事务结束后被自动清理                                                 |
| XID 传递      | 看 order / product 两个服务的启动与请求日志     | 出现 `Begin new global transaction` 与 `branch register success`，两边 XID 相同 |

### 故障注入（验证回滚用）

在 `createOrder` 的 `orderMapper.batchInsertItems(orderItems)` **之后**临时加一行，制造"库存已扣、订单明细写一半就炸"的场景：

```java
throw new RuntimeException("测试全局回滚");
```

下单后观察商品 stock 是否全部回补，验证完把这一行删掉。

**这是本次最关键的验证**：如果库存没有回滚，说明 AT 没生效，优先排查下面「XID 传递」那条。

## 设计要点

### 为什么 `@GlobalTransactional` 和 `@Transactional` 都要保留

两者职责不同，不是重复：

- `@GlobalTransactional`：TM 角色，向 TC 申请 XID，并通过 Feign 把 XID 传给下游
- `@Transactional`：本地事务边界，让"订单主表 + 明细"作为**一个**分支事务整体提交

只留 `@GlobalTransactional` 的话，每条 mapper 操作各自 autocommit、各自注册一个分支事务，既低效又把主表与明细的原子性拆散。

### `enable-auto-data-source-proxy` 为什么必须开

AT 模式的*回滚快照（undo_log）是 Seata 通过****代理 DataSource****&#x20;拦截业务 SQL 生成的。关掉它，Seata 就不知道改了什么数据，分支事务不会*注册、异常也不会回滚——此时注解看起来一切正常，但分布式事务是**静默失效**的，这个坑很隐蔽。

### undo_log 为什么只在 user-service 建一份

三个服务共用同一个 `mall_db`，undo_log 是库级公共表，建三遍纯属重复维护。约定由最先启动的 `user-service` 通过 `spring.sql.init` 执行自己的 `schema.sql` 时创建（语句用 `CREATE TABLE IF NOT EXISTS`，重复执行安全）。

**注意**：共用同一个库 ≠ 共用同一个事务。跨服务的 Feign 调用仍然分属各自独立的本地事务，Seata 依然是必需的。

### SEATA_IP 为什么必须写宿主局域网 IP

Seata 注册到 Nacos 时上报的是容器内网 IP（172.x），宿主机会拿这个地址去连 TC，连不上。而 `SEATA_IP` 环境变量**会忽略 loopback**，填 `127.0.0.1` 无效，必须填宿主机的局域网 IP。换网络环境时只改 `.env` 的 `SEATA_IP` 一处即可。

### Feign 与 Sentinel 并存时的 XID 传递（重点排查项）

本项目开了 `feign.sentinel.enabled: true`，Sentinel 会包装 Feign client。若 XID 没传下去，`product-service` 的分支事务就注册不进全局事务，表现为**库存不回滚、但接口也不报错**——静默失败。

排查手段：请求时看 product-service 日志有没有 `branch register success`；没有就说明 XID 断了。

## 待办（不在本次范围）

- `OrderServiceImpl.closeExpiredOrder`：由 MQ 消费者触发的关单 + 回补库存链路，目前仍是"尽力而为"的单条回补，**未纳入 Seata 全局事务**。它跨服务但入口是 MQ 而非 HTTP，接入方式与下单链路不完全一致，留到 P2 处理
- Seata Server 当前用 `file` 模式存储事务日志，生产应改 `db` 模式并建 `global_table / branch_table / lock_table`

## 踩坑记录

- **Server 与客户端版本必须对齐**：先把 Server 从 1.5.2 升到 1.6.1 再接客户端，否则协议不兼容
- **starter 版本号跟 SCA 走**：写死 1.6.1 反而会出错，交给 BOM 管理
- **docker pull 不走 shell 代理**：`export HTTP_PROXY` 对 docker daemon 无效（daemon 是后台服务，不读 shell 变量），需要在 Docker Desktop 里单独配代理
