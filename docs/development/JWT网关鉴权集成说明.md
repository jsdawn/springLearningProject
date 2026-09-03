# JWT 网关鉴权集成说明（Gateway + jjwt）

## 目标

- user-service 提供登录/注册/注销接口，签发 JWT
- gateway-service 全局拦截鉴权：白名单放行，其余校验 JWT
- 注销立刻生效：token.jti 写入 Redis 黑名单
- 单点/踢人：Redis 维护 userId -> currentJti，新登录覆盖旧 jti
- 统一返回体：HTTP 200 + {code,message,data}

## 依赖

- Nacos：作为配置中心（读取 jwt.secretBase64 等配置）
- Redis：存储黑名单与单点登录状态
- MySQL：user-service 的用户表需要包含 password 字段；已有库需执行一次迁移脚本

## Nacos 配置

建议在 Nacos 创建一个共享配置（所有服务共用），并且每个服务可选创建自己专属配置：

- dataId：common-jwt.yaml（必需，用于 jwt.*）
- dataId：{spring.application.name}.yaml（可选，用于服务自身扩展配置）
- group：DEFAULT_GROUP（或与服务一致）

示例内容：

```yaml
jwt:
  secretBase64: "请替换为你的Base64密钥"
  issuer: "springLearningProject"
  accessTokenExpireSeconds: 3600
  headerName: "Authorization"
  headerPrefix: "Bearer "
```

### 生成 Base64 密钥（示例）

PowerShell 示例：

```powershell
$bytes = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
[Convert]::ToBase64String($bytes)
```

## Gateway 白名单配置

网关白名单在 gateway-service 的配置中维护（可迁移到 Nacos 的 gateway-service.yaml）：

```yaml
gateway:
  auth:
    whitelist:
      - /api/user/auth/login
      - /api/user/auth/register
```

说明：外部请求路径为 /api/user/**，网关路由 StripPrefix=2 后，下游 user-service 实际路径为 /auth/**。

## Redis Key 约定

- 黑名单：auth:blacklist:{jti} = 1（TTL=token剩余有效期）
- 单点：auth:sso:{userId} = currentJti（TTL=accessToken有效期）

## 调用流程

1. 注册：POST /api/user/auth/register（白名单放行）
2. 登录：POST /api/user/auth/login -> 返回 token
3. 访问业务接口：请求头携带 Authorization: Bearer {token}
4. 注销：POST /api/user/auth/logout -> 立刻失效（黑名单）
5. 新登录：覆盖 auth:sso:{userId}，旧 token 立刻失效（踢人）

## 数据库迁移（user-service）

如果你的 users 表已存在且没有 password 字段，登录接口会报 Unknown column 'password'。需要手动执行一次迁移 SQL：

- [V2__add_users_password.sql](file:///c:/Users/jsdawn/Codes/java/springLearningProject/user-service/src/main/resources/db/migration/V2__add_users_password.sql)
