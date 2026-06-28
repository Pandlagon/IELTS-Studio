# Deployment Configuration

> 本文件说明 IELTS Studio 生产部署所需的环境变量、配置项与安全注意事项。
> 配套阅读：[ai-provider-architecture.md](./ai-provider-architecture.md)、[security-and-quota-plan.md](./security-and-quota-plan.md)、[ai-provider-smoke-test.md](./ai-provider-smoke-test.md)。
>
> 快速上手：复制根目录 `.env.example` 为 `.env`，按本文档说明填入实际值。

---

## 1. Required backend environment variables（生产必填）

| 变量 | 说明 | 示例 / 默认 |
|---|---|---|
| `DB_URL` | MySQL JDBC 连接 URL | `jdbc:mysql://<host>:3306/ielts_studio?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true` |
| `DB_USERNAME` | MySQL 账号（生产专用，非 root） | `ielts_studio` |
| `DB_PASSWORD` | MySQL 强密码 | 32 位以上随机字符串 |
| `JWT_SECRET` | JWT 签名密钥 | 64 位以上随机字符串 |
| `AI_KEY_ENCRYPTION_SECRET` | 用户自填 API Key 的 AES/GCM 加密密钥 | 32 位以上随机字符串 |
| `CORS_ALLOWED_ORIGINS` | 前端实际域名（逗号分隔） | `https://example.com,https://www.example.com` |

> ⚠️ 以上变量在生产环境**必须**通过环境变量注入，不能依赖 `application.yml` 中的开发默认值。

---

## 2. Optional backend environment variables（可选）

| 变量 | 默认 | 说明 |
|---|---|---|
| `SERVER_PORT` | `8080` | 后端端口 |
| `SPRING_PROFILES_ACTIVE` | （空） | 本地开发设为 `local` 加载 `application-local.yml`；生产可设为 `prod` |
| `APP_REDIS_ENABLED` | `false` | 是否启用 Redis 分布式限流。多实例部署必须设为 `true` |
| `SPRING_DATA_REDIS_HOST` | `127.0.0.1` | Redis 主机 |
| `SPRING_DATA_REDIS_PORT` | `6379` | Redis 端口 |
| `SPRING_DATA_REDIS_PASSWORD` | （空） | Redis 密码，生产建议设置 |
| `SPRING_DATA_REDIS_DATABASE` | `0` | Redis database 编号 |
| `INIT_ADMIN_USERNAME` | （空） | 启动时自动初始化的内置管理员用户名；三项同时配置且库中无 ADMIN 时生效 |
| `INIT_ADMIN_EMAIL` | （空） | 内置管理员邮箱 |
| `INIT_ADMIN_PASSWORD` | （空） | 内置管理员初始密码（明文注入，后端启动后立即 BCrypt 哈希存储，不落盘明文） |

---

### 2.1 Built-in admin bootstrap（内置管理员初始化）

后端 `AdminBootstrapConfig` 在应用启动时通过 `ApplicationRunner` 执行幂等初始化：

- 读取 `INIT_ADMIN_USERNAME` / `INIT_ADMIN_EMAIL` / `INIT_ADMIN_PASSWORD`（对应 `app.init-admin.*`）。
- **三项任意为空 → 跳过**（不影响现有行为，未配置即不创建）。
- **库中已存在任意 ADMIN（含已禁用 `deleted=1`）→ 跳过**，避免重启重复创建、避免初始密码被重新激活。
- **目标 username/email 未被占用 → 创建新 ADMIN 用户**。
- **目标 username/email 已被占用（且库中无 ADMIN）→ 将该现有 USER 升级为 ADMIN 并重置密码**；若该账号被禁用则一并启用。（幂等：升级后该用户成为 ADMIN，下次启动因已有 ADMIN 跳过，密码不会被再次重置）
- 密码使用 `PasswordEncoder.encode(...)` 存储 BCrypt 哈希，**日志只记录 username，不记录明文密码**。
- 初始化/升级成功后，该环境变量即不再生效（因幂等判断），后续密码修改走后台「用户管理」。

**生产推荐流程：**

1. 首次部署时注入 `INIT_ADMIN_*` 三项环境变量。
2. 启动后端，日志出现 `管理员账号初始化完成：username=...` 或 `已将现有用户升级为管理员并重置密码：username=...` 即成功。
3. 用该账号登录后台，**立即在「用户管理」页面重置密码**为高强度密码。
4. 之后可清除 `INIT_ADMIN_PASSWORD` 环境变量（保留也不影响，因幂等跳过）。

> 若库中已存在 ADMIN 但需要新增管理员，直接由现有 ADMIN 在后台「用户管理」提升其他用户角色即可，无需再用环境变量。

---

## 3. AI provider built-in keys（BUILTIN 模式）

BUILTIN 模式下，站点使用内置 API Key 调用 AI provider，用户消耗站点 credits。

| 变量 | 用途 | 默认 |
|---|---|---|
| `DEEPSEEK_API_KEY` | DeepSeek 文本解析 / 写作评分 / AI 助手 / 翻译 / 完形填空 | （空，BUILTIN 模式必填） |
| `DEEPSEEK_BASE_URL` | DeepSeek API 地址 | `https://api.deepseek.com` |
| `DEEPSEEK_MODEL` | DeepSeek 模型名 | `deepseek-chat` |
| `AI_PRECISE_PROVIDER` | 精准解析提供方：`mimo` 或 `qwen` | `mimo` |
| `MIMO_API_KEY` | 小米 MiMO（图表/表格场景） | （空，`AI_PRECISE_PROVIDER=mimo` 时必填） |
| `MIMO_BASE_URL` | MiMO API 地址 | `https://api.xiaomimimo.com/v1` |
| `MIMO_MODEL` | MiMO 模型名 | `mimo-v2.5` |
| `QWEN_API_KEY` | 通义千问 Qwen 视觉解析 | （空，`AI_PRECISE_PROVIDER=qwen` 时必填） |
| `QWEN_BASE_URL` | Qwen API 地址 | `https://dashscope.aliyuncs.com/compatible-mode/v1` |
| `QWEN_MODEL` | Qwen 模型名 | `qwen3.6-plus` |

> **USER 模式**（用户自填 API Key）不需要上述内置 key；用户在 Profile 页面配置自己的 provider / baseUrl / model / apiKey，不消耗站点 credits，但仍有 rate limit。

---

## 4. USER mode API key encryption

用户自填的 API Key 存储在 `user_ai_settings` 表的 `*_api_key_encrypted` 字段，使用 `AI_KEY_ENCRYPTION_SECRET` 进行 AES/GCM/NoPadding 加密。

**生产注意事项：**

- `AI_KEY_ENCRYPTION_SECRET` 必须注入 32 位以上随机字符串，不能依赖开发 fallback。
- **更换 `AI_KEY_ENCRYPTION_SECRET` 后，旧 encrypted key 将无法解密**，用户需重新填写 API Key。
- 缺失时后端会回退到开发态密钥并打印 WARN 日志，**禁止用于生产**。
- 前端只展示 masked key（如 `sk-****abcd`），不返回 raw key / encrypted key。

---

## 5. Redis rate limit

| 配置 | 行为 |
|---|---|
| `APP_REDIS_ENABLED=false`（默认） | `AiRedisRateLimiter` bean 不创建，`AiUsageGuard` 使用单机内存限流；应用不连接 Redis |
| `APP_REDIS_ENABLED=true` | `AiRedisRateLimiter` 使用 `StringRedisTemplate`，多实例共享计数；Redis 不可用时自动 fallback 到内存限流，不向用户暴露 Redis 异常 |

**生产部署提醒：**

- **多实例部署必须设为 `true`**，否则各实例独立计数，rate limit 不共享，用户可绕过限制。
- 单实例部署可设为 `false`，使用内存限流即可。
- Redis key 形如 `ai:rate:user:{userId}:feature:{feature}:minute:{epochMinute}`，不含敏感信息。
- 限流触发仍写 `ai_usage_records` 的 `REJECTED` 流水（含 provider 字段）。

---

## 6. Database initialization

首次部署前必须执行初始化脚本：

```bash
mysql -u root -p < backend/src/main/resources/db/init.sql
```

脚本会创建 `ielts_studio` 数据库与全部数据表，包括 AI Provider 主线相关表：

- `user_ai_settings`：用户 AI 设置（含加密 API Key）
- `ai_usage_quota`：用户额度（含 `(user_id, period_start)` 唯一约束）
- `ai_usage_records`：使用流水（含 `provider` 字段）

> 增量变更参考 `docs/database-change-guide.md` 中的 migration note。

---

## 7. Frontend environment variables

| 变量 | 默认 | 说明 |
|---|---|---|
| `VITE_API_BASE_URL` | `/api` | 后端 API 基础地址。同源部署可留空走 `/api`；跨域部署填后端完整地址 |

前端配置文件位于 `frontend/.env.example`，复制为 `frontend/.env`（或 `.env.local`）并填入实际值。

---

## 8. Production security checklist

部署前逐项确认：

- [ ] `JWT_SECRET` 已替换为 64 位以上随机字符串（禁止使用 `application.yml` 中的开发默认值）。
- [ ] `AI_KEY_ENCRYPTION_SECRET` 已注入 32 位以上随机字符串。
- [ ] `DB_USERNAME` / `DB_PASSWORD` 使用专用账号与强密码（禁止 root / 空密码）。
- [ ] `CORS_ALLOWED_ORIGINS` 已设为实际前端域名（禁止保留 localhost）。
- [ ] 多实例部署已设 `APP_REDIS_ENABLED=true` 并配置 Redis 连接。
- [ ] BUILTIN 模式已注入对应 provider 的内置 API Key（`DEEPSEEK_API_KEY` 等）。
- [ ] `init.sql` 已在首次部署前执行。
- [ ] 管理员账号 `role=ADMIN` 才能访问 `/admin/**`；普通用户访问返回 403。
- [ ] MinIO 如启用，已替换默认 `minioadmin/minioadmin123`。
- [ ] 后端日志级别为 WARN / INFO，不输出 Authorization / Bearer / sk- 片段。
- [ ] 后端 `mvn test` 通过。
- [ ] 前端 `npm run build` 通过。
- [ ] 已执行 `docs/ai-provider-smoke-test.md` 中的 smoke test checklist。

---

## 9. Local development example

本地开发无需设置所有环境变量，`application.yml` 已内置开发默认值。推荐使用 `.env` 文件集中管理本地敏感配置。

### 方式 A：根目录 `.env`（推荐）

`application.yml` 已通过 `spring.config.import` 自动加载根目录 `.env`（`optional:file:../.env[.properties]`），复制模板后填入实际值即可，**无需指定 profile**：

```bash
# 在仓库根目录执行
cp .env.example .env
# 编辑 .env，填入本地实际的 DB_PASSWORD / DEEPSEEK_API_KEY 等
```

`.env` 示例（仅需填写本地有值的项，其余留空走默认）：

```env
DB_PASSWORD=your_local_db_password
DEEPSEEK_API_KEY=your_deepseek_key
QWEN_API_KEY=your_dashscope_key
MIMO_API_KEY=your_mimo_key
AI_KEY_ENCRYPTION_SECRET=your_local_encryption_secret
# 首次启动自动创建内置管理员（创建后可清空）
INIT_ADMIN_USERNAME=admin
INIT_ADMIN_EMAIL=admin@example.com
INIT_ADMIN_PASSWORD=your_strong_initial_password
```

启动后端：

```bash
cd backend
mvn spring-boot:run
```

> `.env` 已在 `.gitignore` 中，不会被提交。`spring.config.import` 使用 `optional:` 前缀，文件不存在时不会报错。

### 方式 B：`application-local.yml`（兼容旧方式）

如已有 `backend/src/main/resources/application-local.yml`（已在 `.gitignore`），仍可通过 profile 激活：

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### 方式 C：环境变量

```bash
# PowerShell
$env:DB_PASSWORD="your_local_db_password"
$env:DEEPSEEK_API_KEY="your_deepseek_key"
mvn spring-boot:run
```

### 前端

```bash
cd frontend
npm install
npm run dev
```

默认访问 `http://localhost:3000`，如需修改后端地址创建 `frontend/.env.local`：

```env
VITE_API_BASE_URL=http://localhost:8080/api
```
