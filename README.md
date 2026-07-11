# Spring Boot 多模块微服务学习骨架

这是一个基于 `JDK 1.8 + Spring Boot 2.7.18 + Maven + MyBatis` 的多模块微服务示例工程。

## 工程结构

```text
springProject
├─ pom.xml                  父工程
├─ user-service             用户服务
└─ order-service            订单服务
```

## 环境要求

- JDK 1.8
- Maven 3.6+

## 模块说明

### 1. user-service

- 启动类：`com.example.userservice.UserServiceApplication`
- 端口：`8081`
- 示例接口：`GET /users`

### 2. order-service

- 启动类：`com.example.orderservice.OrderServiceApplication`
- 端口：`8082`
- 示例接口：`GET /orders`

## 当前分层

每个服务都包含以下结构：

- `controller`
- `service`
- `service.impl`
- `mapper`
- `resources/mapper`

## 默认数据库

两个服务当前都使用 H2 内存数据库，方便你直接学习和启动：

- `user-service` 使用 `userdb`
- `order-service` 使用 `orderdb`

## 在 IDEA 中如何启动

1. 以 Maven 项目方式打开根目录 `springProject`
2. 等待父工程和两个子模块加载完成
3. 分别运行：
   - `UserServiceApplication`
   - `OrderServiceApplication`

## 在 Trae / VS Code 中如何启动

本项目默认不提交 `.vscode/launch.json` 和 `.vscode/settings.json`，避免把个人本地调试配置带进 Git。

如果你需要在 Trae / VS Code 中本地调试，可以在项目根目录手动创建 `.vscode` 文件夹，并加入下面两个文件。

### 1. `.vscode/launch.json`

用于在编辑器里直接启动两个微服务：

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

### 4. 用户级 `settings.json` 示例

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

- 这里是用户级配置，不建议提交到 Git
- `path` 请改成你自己机器上的 JDK 路径
- 如果你本地同时装了多个 JDK，建议确认项目运行 JDK 与这里的配置保持一致

### 5. 为什么不提交 `.vscode`

因为这些文件通常带有明显的个人环境信息，比如：

- 本地 JDK 路径
- 本地调试参数
- 个人扩展偏好
- 临时运行配置

所以仓库里统一忽略 `.vscode/`，需要的人按 `README` 自行创建即可。

## 启动后可访问

- 用户服务：`http://localhost:8081/users`
- 订单服务：`http://localhost:8082/orders`

## 如果你要改成 MySQL

1. 在子模块 `pom.xml` 中加入 MySQL 驱动依赖
2. 修改对应模块的 `application.yml`
3. 删除或停用各模块下的 `schema.sql`、`data.sql`
