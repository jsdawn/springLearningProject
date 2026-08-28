# Sentinel 集成说明（限流 / 熔断降级 / 规则 Nacos 持久化）

适用于本项目：SpringBoot 2.7.18 + Spring Cloud 2021.0.5 + Spring Cloud Alibaba 2021.0.5.0（对应 Sentinel 1.8.6）。

## 1. 集成概览

| 模块 | 接入内容 |
| --- | --- |
| 父 pom | Sentinel 构件版本由 `spring-cloud-alibaba-dependencies` BOM 统一托管，子模块引入时不写版本 |
| common-core | 通用限流/熔断统一返回处理器 `SentinelBlockExceptionHandler`（Servlet 服务共用） |
| user-service / product-service / order-service | sentinel starter + `sentinel-datasource-nacos`，规则从 Nacos 配置中心读取 |
| order-service | 额外开启 `feign.sentinel.enabled: true`，Feign 调用纳入 Sentinel 统计 |
| gateway-service | sentinel starter + `spring-cloud-alibaba-sentinel-gateway`（网关适配模块，提供 gw-flow Converter 与 SentinelGatewayFilter 自动装配）+ 网关流控规则（`gw-flow`）持久化到 Nacos + 自定义网关限流返回 |

核心原则：**规则一律不写在代码里，全部维护在 Nacos 配置中心**；Sentinel 控制台仅用于监控与查看，修改规则请改 Nacos 配置（实时推送生效，无需重启）。

## 2. 依赖说明（均不写版本，BOM 托管）

```xml
<!-- 限流/熔断降级 starter -->
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-sentinel</artifactId>
</dependency>

<!-- 规则持久化到 Nacos 配置中心 -->
<dependency>
    <groupId>com.alibaba.csp</groupId>
    <artifactId>sentinel-datasource-nacos</artifactId>
</dependency>
```

gateway-service 额外引入 `spring-cloud-alibaba-sentinel-gateway`（而非单独引入 `sentinel-spring-cloud-gateway-adapter`）：

```xml
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-alibaba-sentinel-gateway</artifactId>
</dependency>
```

该模块的作用：

- 自动注册 `sentinel-json-gw-flow-converter`、`sentinel-json-gw-api-group-converter` Bean（`SentinelGatewayAutoConfiguration` 内部类 `SentinelJsonConfiguration`），使 Nacos `gw-flow` 数据源能正常解析 `GatewayFlowRule` JSON
- `SentinelSCGAutoConfiguration` 自动注册 `SentinelGatewayFilter`、`SentinelGatewayBlockExceptionHandler` 两个 Bean
- 传递引入 `sentinel-spring-cloud-gateway-adapter`、`sentinel-api-gateway-adapter-common` 等 Sentinel 原生适配 jar

> **注意**：若仅引入 `sentinel-spring-cloud-gateway-adapter` 而不引入此模块，网关启动时 gw-flow 数据源会报 `NoSuchBeanDefinitionException: No bean named 'sentinel-json-gw-flow-converter'`（错误被 catch 不影响启动，但 gw-flow 规则持久化不生效）。

## 3. application.yml 配置说明

各服务已添加的配置（以 product-service 为例）：

```yaml
spring:
  cloud:
    sentinel:
      eager: true                     # 启动即连接控制台（否则首次请求才连接）
      http-method-specify: true       # 资源名带方法前缀：GET:/products/{id}
      transport:
        dashboard: ${SENTINEL_DASHBOARD:localhost:8858}   # 控制台地址
        port: 8719                    # 客户端命令端口，同机多实例自动递增
      datasource:
        flow:                         # 限流规则数据源
          nacos:
            server-addr: ${NACOS_SERVER_ADDR:localhost:8848}
            namespace: ${NACOS_NAMESPACE:}
            group-id: ${NACOS_GROUP:DEFAULT_GROUP}
            data-id: ${spring.application.name}-flow-rules
            data-type: json
            rule-type: flow
        degrade:                      # 熔断降级规则数据源
          nacos:
            server-addr: ${NACOS_SERVER_ADDR:localhost:8848}
            namespace: ${NACOS_NAMESPACE:}
            group-id: ${NACOS_GROUP:DEFAULT_GROUP}
            data-id: ${spring.application.name}-degrade-rules
            data-type: json
            rule-type: degrade
```

网关略有不同：网关流控使用 `GatewayFlowRule`，`rule-type: gw-flow`，dataId 为 `gateway-service-gw-flow-rules`。

order-service 额外配置：

```yaml
feign:
  sentinel:
    enabled: true   # Feign 调用纳入 Sentinel 统计（未配 fallback，被拦截时走全局异常处理）
```

## 4. Nacos 配置中心需创建的规则配置

配置格式：JSON；Group：`DEFAULT_GROUP`（与 `${NACOS_GROUP}` 保持一致）。

### 4.1 product-service-flow-rules（限流演示）

```json
[
  {
    "resource": "GET:/products",
    "limitApp": "default",
    "grade": 1,
    "count": 1,
    "strategy": 0,
    "controlBehavior": 0,
    "clusterMode": false
  }
]
```

字段说明：`grade=1` 按 QPS 限流；`count=1` 阈值 1 QPS（演示用易触发值，连续两次请求即被限流）；`strategy=0` 直接限流；`controlBehavior=0` 快速失败。

### 4.2 product-service-degrade-rules（熔断降级演示）

```json
[
  {
    "resource": "GET:/products/{id}",
    "grade": 1,
    "count": 0.5,
    "timeWindow": 10,
    "minRequestAmount": 1,
    "statIntervalMs": 10000
  }
]
```

字段说明：`grade=1` 按异常比例熔断；`count=0.5` 异常比例阈值 50%；`statIntervalMs=10000` 统计窗口 10 秒；`minRequestAmount=1` 最小请求数 1；`timeWindow=10` 熔断时长 10 秒（到期进入半开探测，成功则恢复）。

> 资源名 `GET:/products/{id}` 依赖 `ProductUrlCleaner` 将 `/products/123` 归并为 `/products/{id}`，否则会按具体 id 碎片化统计。

### 4.3 其余服务（初始可为空数组，按需添加）

- `user-service-flow-rules`：`[]`
- `user-service-degrade-rules`：`[]`
- `order-service-flow-rules`：`[]`
- `order-service-degrade-rules`：`[]`

### 4.4 gateway-service-gw-flow-rules（网关流控示例）

```json
[
  {
    "resource": "product-service",
    "resourceMode": 0,
    "grade": 1,
    "count": 100,
    "intervalSec": 1,
    "controlBehavior": 0,
    "burst": 0,
    "maxQueueingTimeoutMs": 0
  }
]
```

字段说明：`resource` 为路由 id（与 gateway 路由配置的 id 一致）；`resourceMode=0` 路由维度；`count=100` 每秒 100 次（宽松示例值）。

### 4.5 生产环境配置建议

> 以下为生产环境推荐配置，**阈值需根据实际压测结果调整**，切勿直接复制。

#### 4.5.1 限流规则（生产）

```json
[
  {
    "resource": "GET:/products",
    "limitApp": "default",
    "grade": 1,
    "count": 500,
    "strategy": 0,
    "controlBehavior": 2,
    "warmUpPeriodSec": 10,
    "clusterMode": false
  },
  {
    "resource": "GET:/products/{id}",
    "limitApp": "default",
    "grade": 1,
    "count": 1000,
    "strategy": 0,
    "controlBehavior": 1,
    "maxQueueingTimeMs": 500,
    "clusterMode": false
  }
]
```

生产配置要点：

| 字段 | 演示值 | 生产建议 | 说明 |
|------|--------|---------|------|
| `count` | 1 | 根据压测结果（如 500~1000） | 单机 QPS 阈值，需结合实例数计算 |
| `controlBehavior` | 0（快速失败） | 1（匀速排队）或 2（预热） | 快速失败用户体验差，排队/预热更平滑 |
| `warmUpPeriodSec` | 无 | 10~30 秒 | 冷启动时逐步放行，防止瞬间流量打垮服务 |
| `maxQueueingTimeMs` | 无 | 500~2000ms | 排队等待超时时间，超时后快速失败 |
| `clusterMode` | false | 多实例时考虑 true | 集群限流需额外部署 Token Server |

#### 4.5.2 熔断规则（生产）

```json
[
  {
    "resource": "GET:/products/{id}",
    "grade": 2,
    "count": 500,
    "timeWindow": 30,
    "minRequestAmount": 10,
    "statIntervalMs": 30000
  },
  {
    "resource": "GET:/products/{id}",
    "grade": 1,
    "count": 0.3,
    "timeWindow": 30,
    "minRequestAmount": 10,
    "statIntervalMs": 30000
  }
]
```

生产配置要点：

| 字段 | 演示值 | 生产建议 | 说明 |
|------|--------|---------|------|
| `grade` | 1（异常比例） | 同时配置 1（异常比例）+ 2（慢调用比例） | 多维度熔断更全面 |
| `count`（异常比例） | 0.5（50%） | 0.2~0.3（20%~30%） | 生产环境阈值应更保守 |
| `count`（慢调用 RT） | 无 | 500~2000ms | 响应时间超过阈值视为"慢调用" |
| `minRequestAmount` | 1 | 10~50 | 样本量太小统计不准确，容易误熔断 |
| `statIntervalMs` | 10000（10秒） | 30000（30秒） | 统计窗口越长越稳定，但响应慢 |
| `timeWindow` | 10（10秒） | 30~60 秒 | 熔断时间太短可能反复震荡 |

#### 4.5.3 系统保护规则（生产推荐）

在 `product-service-flow-rules` 同命名空间下创建 `product-service-system-rules`（需在 yml 中添加对应 datasource 配置）：

```json
[
  {
    "highestSystemLoad": 3.0,
    "avgRt": 1000,
    "maxThread": 100,
    "qps": 2000,
    "highestCpuUsage": 0.8
  }
]
```

系统保护是**全局兜底**，当系统负载超过阈值时拒绝所有新请求，防止雪崩。

#### 4.5.4 阈值确定方法

1. **基准压测**：使用 JMeter / wrk 对单接口进行压测，找到单机最大 QPS（RT < 200ms 的前提下）
2. **安全系数**：生产阈值 = 压测峰值 × 0.7~0.8（留 20%~30% 余量）
3. **多实例计算**：集群总容量 = 单机阈值 × 实例数；若开启集群限流，`count` 填集群总容量
4. **渐进调整**：上线初期阈值设保守，观察一周后逐步调优

#### 4.5.5 生产环境 yml 补充配置

若需启用系统保护规则，在 `application.yml` 的 `datasource` 下添加：

```yaml
system:
  nacos:
    server-addr: ${NACOS_SERVER_ADDR:localhost:8848}
    namespace: ${NACOS_NAMESPACE:}
    group-id: ${NACOS_GROUP:DEFAULT_GROUP}
    data-id: ${spring.application.name}-system-rules
    data-type: json
    rule-type: system
```

## 5. Sentinel 控制台部署与使用步骤

1. 下载控制台 jar（与客户端版本一致）：
   `https://github.com/alibaba/Sentinel/releases/download/1.8.6/sentinel-dashboard-1.8.6.jar`
2. 启动控制台（端口 8858，避开网关 8080）：

   ```bash
   java -Dserver.port=8858 -Dcsp.sentinel.dashboard.server=localhost:8858 -Dproject.name=sentinel-dashboard -jar sentinel-dashboard-1.8.6.jar
   ```

3. 浏览器访问 `http://localhost:8858`，默认账号密码 `sentinel / sentinel`。
4. 依次启动各微服务。因配置了 `eager: true`，服务启动后会自动注册到控制台；左侧菜单可查看：
   - **簇点链路**：查看资源调用链路与实时指标
   - **实时监控**：QPS、RT、异常数曲线
   - **机器列表**：已接入的客户端实例
5. 规则查看：控制台"流控规则/熔断规则"页会展示从 Nacos 加载的规则。**修改规则请到 Nacos 配置中心修改对应 dataId**，保存后实时推送到服务生效。

> 注意：直接在控制台新增/修改的规则只推送到客户端内存（重启丢失，且会被 Nacos 配置的下一次推送覆盖）。如需"控制台编辑 -> 自动写入 Nacos"的 push 模式，需按 Sentinel 官方文档改造控制台数据源，本项目默认未改造。

## 6. 验证步骤（演示规则）

前置：Nacos 中已创建 4.1、4.2 的配置，启动 product-service（可连同 gateway）。

### 6.1 限流验证（GET:/products，QPS=1）

连续快速请求两次商品列表：

```bash
curl http://localhost:8082/products
curl http://localhost:8082/products
```

第二次（同一秒内）返回：

```json
{"code":429,"message":"请求太频繁，请稍后再试","data":null}
```

经网关验证：`http://localhost:8080/api/product/products`。

### 6.2 熔断降级验证（GET:/products/{id}，异常比例 50%）

1. 请求一个不存在的商品，制造业务异常（计入异常比例统计）：

   ```bash
   curl http://localhost:8082/products/999999
   ```

   返回业务异常：`{"code":400,"message":"Product not found, id=999999","data":null}`
2. 10 秒内再次请求任意商品详情（包括正常 id）：

   ```bash
   curl http://localhost:8082/products/1
   ```

   返回熔断降级统一响应：`{"code":503,"message":"服务熔断降级，请稍后重试","data":null}`
3. 10 秒熔断窗口结束后进入半开状态，下一次请求若成功则恢复正常。

### 6.3 规则动态生效验证

在 Nacos 中将 `product-service-flow-rules` 的 `count` 改为 10 并保存，无需重启服务，立即按新阈值限流；在控制台"簇点链路"可同步看到规则变化。

## 7. 统一返回格式约定

| 场景 | HTTP 状态 | 响应体 |
| --- | --- | --- |
| 限流 / 热点参数限流 | 200 | `{"code":429,"message":"请求太频繁，请稍后再试","data":null}` |
| 熔断降级（Sentinel 规则触发） | 200 | `{"code":503,"message":"服务熔断降级，请稍后重试","data":null}` |
| `@SentinelResource` fallback（有缓存） | 200 | 缓存的商品数据（可能过期，`data` 为真实对象） |
| `@SentinelResource` fallback（无缓存） | 200 | `{"code":500,"message":"服务降级，商品详情暂时无法获取","data":null}` |
| 黑白名单拦截 | 200 | `{"code":403,"message":"请求被黑白名单规则拦截","data":null}` |
| 系统保护规则 | 200 | `{"code":503,"message":"系统负载过高，已触发保护规则，请稍后重试","data":null}` |

与项目现有全局异常处理器保持一致：HTTP 200 + 响应体业务 code。Servlet 服务由 `common-core` 的 `SentinelBlockExceptionHandler` 统一处理限流/熔断；`GlobalExceptionHandler` 处理业务异常并记录 `Tracer.trace()`；`@SentinelResource` 的 `fallback` 方法提供精细降级逻辑（优先返回缓存，无缓存则抛出异常走全局处理）。网关由 `SentinelSCGAutoConfiguration`（自动装配 `SentinelGatewayFilter` / `SentinelGatewayBlockExceptionHandler`）+ `SentinelGatewayConfig`（通过 `GatewayCallbackManager.setBlockHandler` 注册自定义返回体）共同输出同构的 `GatewayResponse`。

## 8. 涉及文件清单

| 文件 | 说明 |
| --- | --- |
| `pom.xml` | 补充 Sentinel BOM 托管说明 |
| `common-core/pom.xml` | 引入 sentinel starter |
| `common-core/.../common/sentinel/SentinelBlockExceptionHandler.java` | Servlet 服务统一限流/熔断返回 |
| `common-core/.../common/exception/GlobalExceptionHandler.java` | 业务异常（`IllegalArgumentException` / `IllegalStateException`）添加 `Tracer.trace()` 以支持 Sentinel 熔断统计 |
| `user-service/pom.xml`、`product-service/pom.xml`、`order-service/pom.xml`、`gateway-service/pom.xml` | 引入 starter + sentinel-datasource-nacos |
| 各服务 `application.yml` | sentinel transport + Nacos 数据源配置 |
| `product-service/.../config/ProductUrlCleaner.java` | 路径变量资源归并 |
| `product-service/.../service/impl/ProductServiceImpl.java` | 商品详情接口使用 `@SentinelResource` 注解，配置 `fallback` 降级逻辑（优先读缓存，无缓存则抛出异常返回 503），并忽略业务异常（`IllegalArgumentException`） |
| `gateway-service/.../config/SentinelGatewayConfig.java` | 网关限流统一返回 |
