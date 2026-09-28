# 15 - CI/CD 集成说明（GitHub Actions + GHCR）

> 阶段14 P6。CI 走 GitHub Actions（push/PR 触发编译+测试），CD 到镜像为止：四个服务多阶段构建推 GHCR，不含自动部署。

## 一、方案选型

| 决策点 | 选择 | 理由 |
|---|---|---|
| CI 平台 | GitHub Actions | 仓库已在 GitHub（jsdawn/springLearningProject），零额外账号；公开仓库免费额度充足 |
| 触发策略 | push main + PR 双触发 | PR 触发让合并前就能看到流水线状态 |
| CD 边界 | 构建镜像推 GHCR，**不做自动部署** | 学习期部署目标不稳定（本地 Docker compose 手动拉起即可）；镜像推 GHCR 用仓库自带 `GITHUB_TOKEN` 认证，不用注册 Docker Hub |
| 镜像 tag | `sha-<短哈希>` + `latest` 双 tag | sha tag 不可变、可回溯到具体提交；latest 始终指向最新 main |
| runner | `ubuntu-latest` | 与本机 Windows 形成跨平台编译验证，编码/路径类问题会在 CI 暴露 |

## 二、流水线结构

```
push/PR ──> ci.yml ──> JDK8+Maven: mvn clean package（含 22 个冒烟测试）
                    └> upload-artifact: 四个服务 jar（保留 90 天）

push main ──> docker.yml ──> matrix 4 服务并行：
                            Dockerfile.<service> 多阶段构建
                            ──> push ghcr.io/jsdawn/<service>:{sha-xxx,latest}
```

两个工作流独立触发、职责分离：ci.yml 管「代码能不能过」，docker.yml 管「产物能不能成镜像」。

## 三、冒烟测试集（ci.yml 的 test 前提）

CI 里跑 `mvn package` 会执行测试，为让测试环节有实际验证内容，补了 6 个测试类（22 个用例），全部**不依赖外部环境**（不连 MySQL/Redis/Nacos），CI 里裸跑：

| 测试类 | 验证内容 | 测试手段 |
|---|---|---|
| common-auth `JwtUtilTest` | JWT 生成/解析/过期篡改 | 纯单元测试 |
| common-core `LoginUserHolderTest` | ThreadLocal 上下文存取/清理 | 纯单元测试 |
| user `RegisterRequestValidationTest` | JSR303 注解（NotBlank/Pattern/Size） | `Validation.buildDefaultValidatorFactory()` 本地校验 |
| order `CreateOrderRequestValidationTest` | 嵌套对象 `@Valid` 级联校验 | 同上 |
| product `ProductPageQueryValidationTest` | @Min/@Max 边界 | 同上 |
| gateway `GatewayAuthPropertiesTest` | `@ConfigurationProperties` 绑定/默认值 | Spring `Binder` 内存 Map 绑定 |

关键点：JSR303 校验测试**不起 Spring 上下文**，直接用 Hibernate Validator 工厂——这是「DTO 校验逻辑」和「Spring 环境依赖」解耦的标准做法，测试毫秒级完成。

## 四、多阶段 Dockerfile 设计

位置约定：`.docker/Dockerfile.<service>`，**构建上下文必须是仓库根目录**（多模块 Maven 需要父 pom 和 common 模块）：

```bash
docker build -f .docker/Dockerfile.order-service -t order-service .
```

分层缓存优化：先只拷 7 个 pom 执行 `dependency:go-offline` 预拉依赖层，再拷源码——源码改动不触发全量依赖下载。

运行层要点：

- `openjdk:8-jre` 纯运行时，镜像不携带 Maven/编译器
- `JAVA_OPTS="-Dfile.encoding=UTF-8"`：容器内 JVM 默认编码不一定是 UTF-8（Linux 常回落 POSIX/ASCII），显式指定——**呼应阶段12② 的 Nacos GBK 坑，容器化后该问题会在运行层复现**，必须在启动参数兜底
- `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0`：JDK 8u191+ 支持容器感知，堆按容器内存配比而非宿主机
- `TZ=Asia/Shanghai`：日志时间戳与本地一致
- `.dockerignore` 排除 `.git`/`target`/`logs`/`docs` 等，减小上下文体积

## 五、GHCR 认证与权限

`docker.yml` 的 job 必须声明：

```yaml
permissions:
  contents: read
  packages: write
```

`secrets.GITHUB_TOKEN` 是 Actions 自动注入的短时效令牌，配合上述权限即可推 `ghcr.io/<owner>/<service>`，首次 push 后镜像默认 private（公开仓库可在包设置里改 public）。

镜像名必须**全小写**——GHCR 硬性要求，服务名本身就是小写所以矩阵直用目录名。

## 六、踩坑与注意

1. **Windows 本地无 Maven**，CI 才是真正的全量编译验证环境；本地用 javac + .m2 classpath 做快速验证（见项目备忘），两者互补
2. **JUnit Console Launcher 不接受 classpath 通配符**（`libs/*`），本地裸跑测试要把 jar 展开成显式列表
3. **`Binder` 绑定空 source 返回 unbound**（bean 保持字段默认值）而非报错——测试默认值场景时不要断言绑定成功，直接断言默认值本身
4. **空串校验违规数**：`@NotBlank` 和 `@Size(min=...)` 对空串**同时触发**，断言违规数时两个注解都要算
5. **dependency:go-offline 对部分插件依赖拉不全**，加 `|| true` 容忍失败——真正编译时 Maven 会自动补齐缺失依赖，只损失少量缓存命中率
6. push 走 Clash 代理（127.0.0.1:7897），节点抖动时 `Recv failure: Connection was reset`，重试即可；代理未启动时端口无监听，先开代理软件
7. **IDE 自动生成的 `*ApplicationTests.contextLoads` 是环境依赖测试**——`@SpringBootTest` 启动完整上下文需要 Nacos/MySQL/Redis，CI 里必挂。三个此类测试已删除（git 历史可找回）；若想保留，应配置 mock 环境或 surefire 排除
8. **`openjdk:8-jre` 已从 Docker Hub 下架**（openjdk 官方镜像停止维护），运行层换 `eclipse-temurin:8-jre`（Temurin 是持续维护的 JDK 8 发行版）；`maven:3.8-openjdk-8` 构建层目前仍可拉取
9. `git credential fill` 在 `credential.helper` 未显式配置时会触发 GCM 的「Select a credential helper」弹窗且不持久化选择，反复调用会反复弹——`git config --global credential.helper manager` 一劳永逸

## 七、后续可扩展

- 加 `mvn test` 失败时的测试报告上传（`dorny/test-reporter`）
- 镜像构建加多架构（`linux/arm64`，用 buildx QEMU）
- 若 Lighthouse 续费，可加 `watchtower` 或 SSH 部署 job 实现「推镜像即部署」
