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
