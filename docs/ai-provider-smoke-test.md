# AI Provider 主线 Smoke Test Checklist

> 本文件是 AI Provider 主线（Phase 2 ~ Phase 6B-2C）发布前可手动执行的 smoke test checklist。
> 配套阅读：[ai-provider-architecture.md](./ai-provider-architecture.md)、[security-and-quota-plan.md](./security-and-quota-plan.md)。
>
> 适用阶段：Phase 7A 发布前验收。每条用例标注预期结果，执行时按顺序勾选。

---

## 0. 前置准备

- [ ] 后端 `mvn test` 通过。
- [ ] 前端 `npm run build` 通过。
- [ ] 数据库已执行 `backend/src/main/resources/db/init.sql`，含 `user_ai_settings` / `ai_usage_quota` / `ai_usage_records` 三张表。
- [ ] 后端以 `-Dspring-boot.run.profiles=local` 启动（加载 `application-local.yml` 中的真实 API Key）。
- [ ] 启动日志中可见 `local` profile 激活，且 `AiProviderRegistry initialized with 4 presets`。
- [ ] 浏览器 devtools Network 面板打开，用于检查 response body 不含敏感字段。

---

## 1. 用户 AI 设置

> 覆盖接口：`GET/PUT /api/users/me/ai-settings`、`GET /api/users/me/ai-settings/providers`

### 1.1 BUILTIN 模式保存

- [ ] 登录普通用户（非 ADMIN）。
- [ ] 打开 Profile 页面，定位到「AI 设置」卡片。
- [ ] 选择「使用站点内置 Key」模式。
- [ ] 点击保存，toast 提示成功。
- [ ] devtools Network 检查 `PUT /api/users/me/ai-settings` 的 response：
  - [ ] 不含 `textApiKeyEncrypted` / `visionApiKeyEncrypted` 字段。
  - [ ] 不含明文 `apiKey`。
  - [ ] 只含 `maskedApiKey`（形如 `sk-****abcd`）或 `hasApiKey=false`。

### 1.2 USER 模式填入 fake key

- [ ] 切换到「使用自己的 API Key」模式。
- [ ] Text Provider 选 `OPENAI_COMPATIBLE`，填入 fake baseUrl（如 `https://example.com/v1`）、model（如 `gpt-4o-mini`）、API Key（如 `sk-fake-test-1234567890`）。
- [ ] Vision Provider 选 `OPENAI_COMPATIBLE`，填入同样的 fake 配置。
- [ ] 点击保存，toast 提示成功。
- [ ] 页面刷新后只显示 masked key（如 `sk-fake****7890`），**不显示** raw key。
- [ ] devtools Network 检查 response 不含 `encrypted` / 明文 key 字段。

### 1.3 清空 key

- [ ] 勾选「清空已保存的 API Key」复选框。
- [ ] 点击保存。
- [ ] 页面刷新后 `hasApiKey=false`，masked key 区域为空。
- [ ] 后端 `user_ai_settings` 表中 `*_api_key_encrypted` 与 `*_api_key_masked` 字段均为 NULL。

---

## 2. 用户 AI Usage

> 覆盖接口：`GET /api/users/me/ai-usage`

### 2.1 默认视图

- [ ] 打开 Profile 页面，定位到「AI 额度」卡片。
- [ ] 卡片能正常加载，不报错。
- [ ] 若无 quota 行（新用户或新周期），显示默认视图：`0 / 30`，剩余 `30 credits`。
- [ ] devtools Network 检查 `GET /api/users/me/ai-usage` 的 response：
  - [ ] 不含 API Key / encrypted key / masked key / baseUrl / model。
  - [ ] 仅含 `keyMode` / `periodStart` / `periodEnd` / `creditsTotal` / `creditsUsed` / `creditsRemaining` / `builtinQuotaEnabled` / `recentRecords`。

### 2.2 调用一次 AI 功能后

- [ ] 调用一次 AI 功能（如写作评分、翻译、AI Chat）。
- [ ] 回到 Profile，「AI 额度」卡片点击刷新。
- [ ] BUILTIN 模式：`creditsUsed` 增加，recent records 新增一条 `SUCCESS` 记录。
- [ ] USER 模式：`creditsUsed` 不变，recent records 新增一条 `SUCCESS` 记录，cost=0。
- [ ] recent records 表展示 Provider 列（DeepSeek / Qwen / MiMO / OpenAI-compatible）。

---

## 3. BUILTIN Quota 不足

### 3.1 额度耗尽

- [ ] 使用低 credits 测试数据，或反复调用直到 `creditsUsed >= creditsTotal`。
- [ ] 再次调用 AI 功能。
- [ ] 前端收到清晰错误提示：「本周 AI 额度已用完，可切换自填 Key 模式」。
- [ ] devtools Network 检查 response 不含 provider 原始 body / key。
- [ ] 后端 `ai_usage_records` 表新增一条 `REJECTED` 记录，`error_message='INSUFFICIENT_CREDITS'`，cost=0，provider 已写入。
- [ ] 后端日志不含 Authorization / Bearer / sk- 片段。

---

## 4. USER Rate Limit

### 4.1 内存限流（Redis disabled）

- [ ] 后端启动时不设置 `APP_REDIS_ENABLED`，或设置 `APP_REDIS_ENABLED=false`。
- [ ] 启动日志确认 `AiRedisRateLimiter` bean 未创建（无相关 INFO 日志）。
- [ ] USER 模式下连续调用同一 AI 功能超过 20 次/分钟。
- [ ] 第 21 次起返回「请求过于频繁，请稍后再试」。
- [ ] 后端 `ai_usage_records` 表新增 `REJECTED` 记录，`error_message='RATE_LIMITED'`，cost=0，provider 已写入。

### 4.2 Redis 限流（Redis enabled）

- [ ] 设置 `APP_REDIS_ENABLED=true`，并配置 `spring.data.redis.host/port`。
- [ ] 后端启动，确认 `AiRedisRateLimiter` bean 创建。
- [ ] USER 模式下连续调用同一 AI 功能超过 20 次/分钟。
- [ ] 第 21 次起返回「请求过于频繁，请稍后再试」。
- [ ] Redis 中可观察到 key 形如 `ai:rate:user:{userId}:feature:{feature}:minute:{epochMinute}`。
- [ ] Redis key 不含 API Key / Bearer / http / model / provider / prompt 片段。
- [ ] 关闭 Redis 后再调用，应用降级到内存限流，AI 功能仍可用（不向用户暴露 Redis 异常）。

---

## 5. 管理端 AI Usage 统计

> 覆盖接口：`GET /api/admin/ai-usage/summary`、`GET /api/admin/ai-usage/recent`

### 5.1 权限

- [ ] ADMIN 用户登录后，NavBar 用户下拉菜单可见「AI 使用统计」入口（移动端菜单同）。
- [ ] USER 用户登录后，NavBar **不显示**「AI 使用统计」入口。
- [ ] USER 用户直接访问 `/admin/ai-usage`，前端路由守卫拦截跳回首页。
- [ ] USER 用户直接调用 `GET /api/admin/ai-usage/summary`，后端返回 403（`/admin/**` hasRole ADMIN 兜底）。

### 5.2 数据加载

- [ ] ADMIN 用户打开 `/admin/ai-usage` 页面。
- [ ] 顶部统计卡片正常展示（totalCalls / success / failed / rejected / totalCost / uniqueUsers）。
- [ ] 5 个分组表（status / provider / feature / keyMode / taskType）正常加载。
- [ ] daily trend 表正常加载，缺失日期补 0。
- [ ] 最近记录表正常加载，按 createdAt 倒序。
- [ ] devtools Network 检查 response：
  - [ ] 不含 API Key / encrypted key / masked key / baseUrl / model。
  - [ ] 不含用户密码 / 用户邮箱。
  - [ ] errorMessage 字段为脱敏后的摘要。

### 5.3 参数 clamp

- [ ] 调用 `GET /api/admin/ai-usage/summary?days=0`，后端 clamp 到 1。
- [ ] 调用 `GET /api/admin/ai-usage/summary?days=999`，后端 clamp 到 90。
- [ ] 调用 `GET /api/admin/ai-usage/recent?limit=0`，后端 clamp 到 1。
- [ ] 调用 `GET /api/admin/ai-usage/recent?limit=999`，后端 clamp 到 100。

---

## 6. 安全检查

### 6.1 浏览器 devtools

- [ ] 全程检查所有 `/api/users/me/ai-settings*`、`/api/users/me/ai-usage*`、`/api/admin/ai-usage*` 接口的 response body：
  - [ ] 不含 raw API Key（如 `sk-xxxxxxxx`）。
  - [ ] 不含 `encrypted` 字段。
  - [ ] 不含 provider 原始 response body。
  - [ ] 不含 baseUrl / model（usage / admin usage 接口）。

### 6.2 后端日志

- [ ] 全程检查后端日志：
  - [ ] 不出现 `Authorization: Bearer xxx`。
  - [ ] 不出现 `sk-` 开头的 key 片段。
  - [ ] AI 调用失败日志只记 `ex.getClass().getSimpleName()`，不记 `ex.getMessage()`。
  - [ ] `ai_usage_records.error_message` 经脱敏（Authorization / Bearer / sk- 替换为 `[REDACTED]`，截断 500 字符）。

### 6.3 Provider 失败错误

- [ ] 故意填入错误的 API Key 触发 provider 401。
- [ ] 前端收到通用提示「AI 服务暂时不可用，请稍后重试」，**不**返回 provider 原始 body。
- [ ] 后端 `ai_usage_records` 写 `FAILED` 记录，errorMessage 为脱敏后的摘要。

---

## 7. DB 一致性快速核对

执行以下 SQL，确认表结构与 Entity 对齐：

```sql
-- user_ai_settings：key_mode / text_* / vision_* 字段齐全
DESCRIBE user_ai_settings;

-- ai_usage_quota：(user_id, period_start) 唯一约束存在
SHOW INDEX FROM ai_usage_quota WHERE Key_name = 'uk_ai_usage_quota_user_period';

-- ai_usage_records：provider 字段存在
DESCRIBE ai_usage_records;
```

- [ ] `user_ai_settings` 含 `key_mode` / `text_provider` / `text_base_url` / `text_model` / `text_api_key_encrypted` / `text_api_key_masked` / `vision_*` 对应字段。
- [ ] `ai_usage_quota` 含 `user_id` / `period_start` / `period_end` / `credits_total` / `credits_used`，且 `(user_id, period_start)` 唯一约束存在。
- [ ] `ai_usage_records` 含 `user_id` / `task_type` / `feature` / `cost` / `key_mode` / `provider` / `status` / `error_message` / `created_at`。

---

## 8. 完成判定

全部用例通过后，AI Provider 主线可视为通过发布前验收。如发现 blocker：

1. 记录复现步骤与预期/实际差异。
2. 仅修复与本阶段相关的 blocker，不顺手重构无关代码。
3. 修复后重新运行 `mvn test` 与 `npm run build`，并重跑相关 smoke test 用例。
