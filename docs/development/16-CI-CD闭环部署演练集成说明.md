# 16 - CI/CD 全链路闭环部署演练（GHCR 镜像 → 可运行系统）

> 阶段14 收尾。目标：验证「git push → CI 测试 → 镜像推 GHCR → 拉镜像 → 可运行系统」闭环，
> 全部服务使用 GHCR 镜像运行，不含任何本地编译产物。定位是**手动 CD（Continuous Delivery）
> 的验收环节**——规范 CD 的环境分层/自动部署/回滚见文末"与规范 CD 的差距"。

## 一、方案设计

### 1.1 目录结构与职责

```
deploy/
├── docker-compose.ghcr.yml      # 闭环部署编排（独立 compose 项目 mall-ghcr）
├── nacos-seed/                  # Nacos 配置种子（common-jwt / gateway-service / order-service）
│   ├── common-jwt.yaml
│   ├── gateway-service.yaml
│   └── order-service.yaml
└── seata/config/application.yml # 测试环境独立 seata 配置（自包含，不依赖 dev-env-docker）
```

### 1.2 与开发环境（dev-env-docker）的关系

| 维度 | 处理方式 |
|---|---|
| compose 项目 | 顶层 `name: mall-ghcr`，与 dev 环境完全隔离，可同时共存 |
| 宿主端口 | 统一 +20000 偏移（网关 28080 / MySQL 23306 / Nacos 28848 / Zipkin 29411 …），避免冲突 |
| 容器互访 | 一律走 compose 服务名（容器内端口不变），中间件宿主端口仅调试用 |
| 中间件 | 软件与版本一致（MySQL 8.0 / Nacos 2.5.3 / Redis 5.0.14 / RabbitMQ 3.12 / Seata 1.6.1 / Zipkin 3.0），全新实例全新卷 |
| 种子数据 | db-seed 挂载**业务代码库**的 `user-service`/`product-service` data.sql——种子跟代码走单一来源，测试环境复用而非复制 |

### 1.3 两个一次性种子容器（`docker compose up -d` 一条命令全自动）

**nacos-seed**（curlimages/curl）：
- `depends_on: nacos: service_healthy` 后执行，向全新 Nacos 发布三份配置
- **common-jwt.yaml 必须存在**：JWT secret 无代码默认值，缺失则登录/鉴权全链路挂
- **密钥不进 git**：种子里的 `common-jwt.yaml` 只放 `__JWT_SECRET__` 占位符，真实密钥由
  `deploy/.env` 的 `JWT_SECRET` 注入 nacos-seed 容器环境，发布前 `sed` 替换（见踩坑 7）
- 业务服务再通过 `nacos-seed: service_completed_successfully` 依赖它，保证 `config.import` 时配置已就位

**db-seed**（mysql 客户端镜像）：
- 轮询等待 `sys_user_role` / `products` 表存在（表由各服务启动时 schema.sql 自动建，MySQL 里没有任何预置 DDL）
- 两表就绪后灌入 user/product 的 data.sql（INSERT IGNORE 幂等）
- **必须加 `--default-character-set=utf8mb4`**（见踩坑 3）

### 1.4 业务服务的环境注入

四服务通过 `x-service-env` YAML 锚点统一注入：`DB_HOST=mysql`、`NACOS_SERVER_ADDR=nacos:8848`、
`REDIS_HOST=redis`、`RABBITMQ_HOST=rabbitmq`、`ZIPKIN_BASE_URL=http://zipkin:9411`、
`SENTINEL_DASHBOARD=sentinel:8858`——与各服务 application.yml 的 `${ENV:default}` 占位一一对应，
镜像不需要任何改动。

### 1.5 Seata 在闭环网络的特殊性

**不设 `SEATA_IP`**。dev 环境必须设宿主局域网 IP，是因为 Seata 客户端跑在 IDE（宿主机）里、
需要可达的注册地址；闭环里 order/product 与 seata-server 同在 compose 网络，注册容器 IP 天然可达。
TC 配置文件独立在 `deploy/seata/config/application.yml`（registry 指向服务名 `nacos:8848`）。

## 二、使用步骤

```bash
# 一条命令拉起全部（首次会自动 pull 4 个 GHCR 镜像 + 种子容器跑完自动退出）
docker compose -f deploy/docker-compose.ghcr.yml up -d

# 观察种子与业务服务就绪
docker compose -f deploy/docker-compose.ghcr.yml ps
docker logs mall-ghcr-db-seed-1        # 出现 "db seed done" 即种子完成

# 验证入口：网关 http://localhost:28080（登录 → 业务接口）
# Zipkin: http://localhost:29411   RabbitMQ 管理台: http://localhost:25673   Nacos: http://localhost:28848

# 全部销毁（含数据卷，下次 up 得到全新环境）
docker compose -f deploy/docker-compose.ghcr.yml down -v
```

登录密码：种子 data.sql 的 bcrypt 哈希若无文档化明文，需先在 mall-ghcr 的 MySQL 里重置
（闭环库是一次性的，直接 UPDATE users SET password 即可）。

## 三、踩坑记录

1. **镜像内容 = 最后一次 push 的内容**。改了代码没 push，GHCR `latest` 就是旧的——闭环验证
   撞上过 `findAll` 参数缺失的旧镜像报错，这本身就是闭环在正确工作。
2. **Docker Hub 直连超时/EOF**：`curlimages/curl` 等小镜像从 daocloud 镜像源拉取后
   `docker tag` 回原名（`docker.m.daocloud.io/curlimages/curl:8.10.1` → `curlimages/curl:8.10.1`）。
3. **db-seed 中文乱码**：mysql 客户端默认 latin1，灌 UTF-8 的 data.sql 会把中文存成乱码，
   必须 `mysql --default-character-set=utf8mb4`。
4. **Nacos 配置种子是必需步骤**：全新 Nacos 没有 common-jwt.yaml 时服务照样能起
   （`optional:` 导入不报错），但登录时才炸——静默失败类问题，靠"配置播种容器 + 服务依赖
   `service_completed_successfully`"把问题提前到部署阶段。
5. **`paths-ignore` 的连带效果**：CI 因 paths-ignore 跳过时，`workflow_run` 不会触发，
   镜像构建自动跟着跳过，docker.yml 无需单独配置。
6. **deploy/ 目录的改动会触发 CI**：`paths-ignore` 目前只忽略 `docs/**` 与 `*.md`，
   deploy/ 编排文件变更仍会跑全量 CI + 重建镜像（对产物无影响的目录可按需加入 ignore）。
7. **密钥不落 git（`.env` 机制）**：compose 中密钥类变量一律 `${VAR:?msg}` 强制外部注入
   （无默认值，缺失直接报错），真实值放 `deploy/.env`（.gitignore 忽略），仓库只提交
   `.env.example` 模板；nacos-seed 用容器环境变量 + `sed` 在发布前替换种子模板里的
   `__JWT_SECRET__` 占位符。**判断口径：跨环境会变/敏感的进 .env，compose 栈内部
   固定不变的拓扑值（服务名、内网端口）留在 compose 里——12-Factor 的 config 指
   "部署之间会变的东西"，不是"所有东西"**。

## 四、与规范 CD 的差距（后续演进方向）

| 要素 | 当前状态 | 规范做法 |
|---|---|---|
| 环境分层 | 单环境（本机 compose） | 测试/生产多环境，同一 `sha-xxx` 镜像晋升 |
| 部署触发 | 手动 `compose up` | 流水线 deploy job（SSH/K8s）或 GitOps（ArgoCD） |
| 密钥管理 | `deploy/.env` 本地注入，不进 git | GitHub Secrets / Vault 等集中托管 + 轮换 |
| 发布策略 | 全量替换 | 滚动更新 / 蓝绿 / 金丝雀 + 健康检查 + 按旧 sha tag 回滚 |
