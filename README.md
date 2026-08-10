# Spring Boot 多模块微服务学习骨架

这是一个基于 `JDK 1.8 + Spring Boot 2.7.18 + Maven + MyBatis` 的多模块微服务示例工程。

## 工程结构

```text
springProject
├─ pom.xml                  父工程
├─ gateway-service          网关服务（Spring Cloud Gateway / WebFlux）
├─ user-service             用户服务
├─ product-service          商品服务
└─ order-service            订单服务
```

## 环境要求

- JDK 1.8
- Maven 3.6+

## 模块说明

### 0. gateway-service

- 启动类：`com.example.gatewayservice.GatewayServiceApplication`
- 端口：`8080`
- 路由前缀：
  - `/api/user/**` → user-service
  - `/api/product/**` → product-service
  - `/api/order/**` → order-service

### 1. user-service

- 启动类：`com.example.userservice.UserServiceApplication`
- 端口：`8081`
- 示例接口：`GET /users`

### 2. order-service

- 启动类：`com.example.orderservice.OrderServiceApplication`
- 端口：`8083`
- 示例接口：`GET /orders`

### 3. product-service

- 启动类：`com.example.productservice.ProductServiceApplication`
- 端口：`8082`
- 示例接口：`GET /products`

## 当前分层

每个服务都包含以下结构：

- `controller`
- `service`
- `service.impl`
- `mapper`
- `resources/mapper`

## 数据库配置

当前建议 `user-service`、`product-service`、`order-service` 共用一个 MySQL 数据库，例如 `mall_db`。

三个服务统一使用根目录 `.env` 中的这组变量：

- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
- `DB_USERNAME`
- `DB_PASSWORD`

## Nacos 配置与启动

当前 `gateway-service`、`user-service`、`product-service`、`order-service` 均已集成 Nacos（2.5.3以下版本） 注册中心，用于服务发现。

### 1. 启动本地 Nacos

从 [Nacos GitHub Releases](https://github.com/alibaba/nacos/releases) 下载 Nacos Server 压缩包，解压后执行：

```bash
# Windows 单机模式启动
startup.cmd -m standalone
```

启动成功后访问：`http://localhost:8848/nacos`（默认账号密码：nacos/nacos）

### 2. Nacos 环境变量

三个 Nacos 相关变量支持通过 `.env` 配置：

| 变量 | 默认值 | 说明 |
|---|---|---|
| `NACOS_SERVER_ADDR` | `localhost:8848` | Nacos 服务地址 |
| `NACOS_NAMESPACE` | （空） | 命名空间 ID，留空使用 public |
| `NACOS_GROUP` | `DEFAULT_GROUP` | 服务分组名称 |

### 3. `.env` 文件补充示例

如果你使用本地 Nacos 默认配置，无需额外配置。如需自定义，在 `.env` 中添加：

```env
NACOS_SERVER_ADDR=localhost:8848
NACOS_NAMESPACE=
NACOS_GROUP=DEFAULT_GROUP
```

## 在 IDEA 中如何启动

1. 以 Maven 项目方式打开根目录 `springProject`
2. 等待父工程和各子模块加载完成
3. 分别运行：
   - `UserServiceApplication`
   - `ProductServiceApplication`
   - `OrderServiceApplication`
   - `GatewayServiceApplication`

## 在 Trae / VS Code 中如何启动

本项目默认不提交 `.vscode/launch.json` 和 `.vscode/settings.json`，避免把个人本地调试配置带进 Git。

如果你需要在 Trae / VS Code 中本地调试，可以在项目根目录手动创建 `.vscode` 文件夹，并加入下面两个文件。

### 1. `.vscode/launch.json`

用于在编辑器里直接启动各个微服务：

```json
{
  "configurations": [
    {
      "type": "java",
      "name": "Spring Boot-OrderServiceApplication<order-service>",
      "request": "launch",
      "cwd": "${workspaceFolder}",
      "mainClass": "com.example.orderservice.OrderServiceApplication",
      "projectName": "order-service",
      "args": "",
      "envFile": "${workspaceFolder}/.env"
    },
    {
      "type": "java",
      "name": "Spring Boot-ProductServiceApplication<product-service>",
      "request": "launch",
      "cwd": "${workspaceFolder}",
      "mainClass": "com.example.productservice.ProductServiceApplication",
      "projectName": "product-service",
      "args": "",
      "envFile": "${workspaceFolder}/.env"
    },
    {
      "type": "java",
      "name": "Spring Boot-UserServiceApplication<user-service>",
      "request": "launch",
      "cwd": "${workspaceFolder}",
      "mainClass": "com.example.userservice.UserServiceApplication",
      "projectName": "user-service",
      "args": "",
      "envFile": "${workspaceFolder}/.env"
    }
  ]
}
```

### 2. `.vscode/settings.json`

这是一个最小可用示例：

```json
{
  "java.jdt.ls.vmargs": "-XX:+UseParallelGC -XX:GCTimeRatio=4 -XX:AdaptiveSizePolicyWeight=90 -Dsun.zip.disableMemoryMapping=true -Xmx4G -Xms100m -Xlog:disable"
}
```

如果你的 Java 扩展无法正常识别 Maven 项目，可以在用户级设置中额外检查这些内容：

- `java.jdt.ls.java.home` 建议使用 `JDK 17+`
- 项目运行 JDK 继续使用 `1.8`
- 不要在 Maven 多模块项目里手动配置 `java.project.sourcePaths`

### 3. 导入项目

1. 打开根目录 `springProject`
2. 安装 Java 扩展和 Maven 扩展
3. 执行 `Java: Import Java Projects in Workspace`
4. 执行 `Developer: Reload Window`
5. 在运行面板选择对应的 `launch` 配置启动服务

### 4. `.env` 文件示例

如果你使用 `launch.json` 里的 `envFile` 方式加载环境变量，可以在项目根目录创建 `.env` 文件。

示例：

```env
DB_HOST=localhost
DB_PORT=3306
DB_NAME=mall_db
DB_USERNAME=root
DB_PASSWORD=
```

说明：

- 这份配置是共用数据库场景下的通用配置
- 当前建议 `user-service`、`product-service`、`order-service` 共用一个数据库，例如 `mall_db`
- 三个服务现在都统一读取这组 `DB_*` 变量
- `.env` 已加入 `.gitignore`，不会默认提交到仓库
- 如果你的 MySQL 用户有密码，把 `DB_PASSWORD` 改成你的实际密码

### 5. 工作区 `settings.json` 示例

```jsonc
// Java语言服务器运行JDK（当前示例使用 JDK 1.8）
"java.jdt.ls.java.home": "C:\\Users\\xx\\MyApp\\JavaEnv\\jdk-8.0.492.9-hotspot",
// 配置运行时，指定默认JDK1.8
"java.configuration.runtimes": [
  {
    "name": "JavaSE-1.8",
    "path": "C:\\Users\\xx\\MyApp\\JavaEnv\\jdk-8.0.492.9-hotspot",
    "default": true
  }
],
// Spring编译、Maven配套配置
"java.compile.nullAnalysis.mode": "automatic",
"java.configuration.updateBuildConfiguration": "automatic",
"java.errors.incompleteClasspath.severity": "warning",
"java.compiler.annotationProcessor.enabled": true,
"maven.terminal.useJavaHome": true,
"java.debug.settings.hotCodeReplace": "auto",
"java.dependency.packagePresentation": "hierarchical",
"xml.server.preferBinary": true
// Maven国内镜像加速（可选，解决依赖下载慢）
// "java.configuration.maven.userSettings": "D:\\maven\\conf\\settings.xml"
```

说明：

- 这里是工作区配置，不建议提交到 Git
- `path` 请改成你自己机器上的 JDK 路径
- 如果你本地同时装了多个 JDK，建议确认项目运行 JDK 与这里的配置保持一致

### 6. 为什么不提交 `.vscode`

因为这些文件通常带有明显的个人环境信息，比如：

- 本地 JDK 路径
- 本地调试参数
- 个人扩展偏好
- 临时运行配置

所以仓库里统一忽略 `.vscode/`，需要的人按 `README` 自行创建即可。

## 启动后可访问

- 用户服务：`http://localhost:8081/users`
- 商品服务：`http://localhost:8082/products`
- 订单服务：`http://localhost:8083/orders`

## 如果你要改成 MySQL

当前 `user-service`、`product-service`、`order-service` 已统一按 MySQL 方式配置。

如果你要继续使用这套配置，请确保：

1. 本地 MySQL 已启动
2. 已创建数据库 `mall_db`，或把 `.env` 中的 `DB_NAME` 改成你的实际库名
3. 三个服务至少各启动一次，自动执行各自的 `schema.sql` 初始化表结构
4. 或者手动执行各服务的 `schema.sql`

## SQL 维护约定

- `src/main/resources/schema.sql`：保存当前服务的最新完整表结构
- `src/main/resources/db/migration/`：保存该服务后续的表结构变更 SQL
- 变更脚本命名按 Flyway 常见规范使用：`V1__init.sql`、`V2__add_xxx.sql`

当前目录位置：

- `user-service/src/main/resources/db/migration/`
- `product-service/src/main/resources/db/migration/`
- `order-service/src/main/resources/db/migration/`
