# Nacos 配置中心集成说明（阶段12 · 二）

> 对应学习路线阶段12②。前置：阶段7/8 接入 Nacos 时 `spring.config.import: optional:nacos:` 链路已就绪（服务发现用的是同一套注册中心，配置中心是另一条独立链路）。  
> 涉及服务：order-service、gateway-service。

## 目标

把「运营期需要调整」的业务参数从代码/本地 yml 搬进 Nacos，打通**改配置不重启**的动态刷新链路；同时明确参数选型边界——不是所有配置都适合进配置中心。

## 先想清楚：什么参数该进配置中心

判断标准是**谁在什么时机改它**：

| 参数 | 原来在哪 | 是否迁移 | 理由 |
|---|---|---|---|
| `order.business.deep-page-offset-threshold` | 硬编码常量 | ✅ 迁 | orders 表体量变化后运营可调 |
| `order.business.idempotent-token-ttl-seconds` | 硬编码常量 | ✅ 迁 | 安全策略类参数，可能收紧 |
| `gateway.auth.whitelist` | 本地 yml | ✅ 迁（本地留兜底） | 典型运营参数：临时放行回调地址、压测放行探活路径 |
| `order.mq.delay-ttl-ms` | 本地 yml | ❌ 不迁 | 见下文 |

**不迁 `delay-ttl-ms` 的原因**：它是延时队列的 `x-message-ttl` **声明属性**——RabbitMQ 只在队列首次声明时读取该值，之后改配置不会更新已存在的队列，必须删除队列重建才生效。这种「改了也不生效」的参数放进动态刷新体系，只会制造误解。结论：**动态刷新只对「读了立刻能用」的参数有意义**，声明式/启动期固化的资源属性不迁。

## 配置契约：配置类即文档

order-service 新增 `config/OrderBusinessProperties.java`：

```java
@Component
@ConfigurationProperties(prefix = "order.business")
public class OrderBusinessProperties {

    /**
     * 深分页阈值：分页 offset 超过该值时切换为延迟关联 SQL（子查询先定位 id 再回表）。
     */
    private int deepPageOffsetThreshold = 1000;

    /**
     * 下单幂等令牌有效期（秒）：超时未提交自动作废，客户端需重新领取。
     */
    private long idempotentTokenTtlSeconds = 300;
    // getter/setter 省略
}
```

设计要点：

1. **字段名、类型、默认值、Javadoc 注释都写在代码里**——这个类就是参数契约。团队成员想知道有哪些可调参数、含义是什么，看这一个类即可，不用去 Nacos 控制台翻配置。
2. **代码默认值兜底**，参数生效优先级形成三层：`Nacos 配置 > 本地 application.yml > 代码默认值`。Nacos 没建配置、服务断连、新环境冷启动都有兜底，不会起不来。
3. 原来硬编码 `1000` / `300` 的使用点（`IdempotentTokenService`、`OrderServiceImpl`）改为注入该类读取。

gateway-service 的白名单同理走 `GatewayAuthProperties`（`@ConfigurationProperties(prefix = "gateway.auth")`，阶段4 已有），本地 yml 的值作兜底。

## 动态刷新机制：为什么不需要 @RefreshScope

这是本篇最容易混的知识点，Spring Cloud 有两套刷新路径：

**@Value 字段**：值在 Bean 初始化时注入一次，之后不会再变。要刷新必须给 Bean 加 `@RefreshScope`——RefreshEvent 触发时把整个 Bean 从容器里销毁，下次访问时用新 Environment 重建（本质是懒加载的重建，Bean 的状态会丢）。

**@ConfigurationProperties 绑定类**：在 `spring.config.import` 模式下，Nacos 变更 → 客户端长轮询收到通知 → 发布 `RefreshEvent` → `ConfigurationPropertiesRebinder` 对所有 `@ConfigurationProperties` Bean **原地重新绑定**（调用同一批 setter 写入新值）。Bean 的引用不变、状态不丢，所以**不需要加 @RefreshScope，不需要重启**。

完整链路：

```
Nacos 控制台发布
  → 客户端长轮询（默认 30s 窗口，变更即时返回）
  → RefreshEvent
  → Environment 重新加载 Nacos 配置
  → ConfigurationPropertiesRebinder rebind 所有绑定类
  → 使用方读到的就是新值
```

**使用方怎么拿值也有讲究**：拿 Bean 引用后**每次请求实时调用 getter**（如 `JwtAuthGlobalFilter` 在过滤逻辑里调 `gatewayAuthProperties.getWhitelist()`），rebind 后立即生效；如果把 getter 返回值缓存到自己的字段里，rebind 就被绕过了。

## 配置模板与同步约定

- `docs/nacos/order-service.yaml`、`docs/nacos/gateway-service.yaml`：配置模板，**git 版本化**。文件头部是模板说明（不发布），yaml 正文是发布到 Nacos 的完整内容。
- `docs/nacos/common-jwt.yaml`：阶段4 建的 JWT 公共配置，从 Nacos 回读同步而来。
- 分工约定：**业务参数进 Nacos，连接信息（datasource/redis/rabbitmq/seata）留本地 yml**——基础设施地址不随业务变动，放配置中心只增加耦合。
- 模板与 Nacos 内容靠**人工同步**（学习期没有多环境，不值得上 nacos-client 脚本化发布）。

## 踩坑：Nacos 配置内容含中文 → 启动报 MalformedInputException

**现象**：启动时报

```
c.a.c.n.c.NacosConfigDataLoader : Error getting properties from nacos: ... dataId='gateway-service.yaml'
org.yaml.snakeyaml.error.YAMLException: java.nio.charset.MalformedInputException: Input length = 1
```

`optional:true` 时服务靠本地配置兜底能起来，但该 dataId 的配置**静默丢失**——不细看日志根本发现不了参数没加载。

**根因链**（配置内容本身是合法 UTF-8，问题出在客户端二次编码）：

1. Nacos 服务端存的是 UTF-8，客户端拉回 `String`——这一步没问题；
2. `NacosDataParserHandler` 把 String 包装成 Resource 时调用的是 **`data.getBytes()` 无参重载**——用 JVM **平台默认编码**转字节（反编译确认：该类只有这一处 getBytes，没有指定 UTF-8）；
3. 中文 Windows + JDK 8 的平台默认编码是 **GBK**（`java -XshowSettings:properties -version` 可查），中文注释被转成 GBK 字节；
4. snakeyaml 的 `UnicodeReader` 按 UTF-8 读这些 GBK 字节 → 第一个非法序列处抛 `MalformedInputException: Input length = 1`。

为什么 `common-jwt.yaml` 没报错：它内容是纯 ASCII，GBK 与 UTF-8 在 ASCII 区间完全兼容。**只要内容里有中文，必踩**。

**修复与防范**：

- 发布到 Nacos 的 yaml 正文**只允许 ASCII 字符**，注释用英文写（中文说明留在 git 模板文件的头部注释区）；
- 或启动参数统一 `-Dfile.encoding=UTF-8`（IDEA Run Configuration 的 VM options / `JAVA_TOOL_OPTIONS`）——能治本但依赖每台开发机配置，不如内容 ASCII 化可靠。

**教训**：「编码问题」不一定出在自己写的代码——第三方框架里一个无参 `getBytes()`，叠加「中文 Windows + JDK8 默认 GBK」的环境事实就会爆。排查这类问题的入口是看异常发生在**编码→解码的哪个交接点**，再逐段核对每一段的字节编码假设。
