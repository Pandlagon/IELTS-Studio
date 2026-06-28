# 安全与额度设计

> 本文件描述 AI 接口的安全防护与额度（quota）设计，**只写设计，不实现代码**。
> 配套阅读：[ai-provider-architecture.md](./ai-provider-architecture.md)、[database-change-guide.md](./database-change-guide.md)。

---

## 1. 为什么公网 AI 接口需要保护

IELTS Studio 的 AI 接口会真实调用第三方 provider（DeepSeek / Qwen / MiMO 等），每次调用都产生费用。
一旦接口暴露在公网且无保护，会面临：

- **额度盗刷**：恶意用户循环调用写作评分、精准解析等高成本接口，耗光站点 API 额度。
- **滥用爬取**：通过 AI Chat / 翻译接口变相免费使用大模型。
- **信息泄露**：provider 原始错误或日志中可能带 Key、内网信息。
- **DoS**：超大输入或高频请求拖垮后端与 provider 链路。

因此必须：**鉴权 + 额度 + 限流 + 输入限制 + 错误脱敏**。

---

## 2. 内置 API Key 模式（BUILTIN）

用户使用站点内置 API Key 调用 AI，消耗站点发放的 **credits**。

### 2.1 额度规则

- 每用户每周发放一定 credits（**建议初始值：30 credits / 周**）。
- 不同功能消耗不同 cost，调用成功后扣费。
- credits 不足时拒绝调用，返回额度不足错误。
- 周期重置（每周一 0 点或滚动 7 天，二选一，实施时定）。

### 2.2 功能 cost 建议

| 功能 | 接口 | cost |
|---|---|---|
| Writing grade（写作评分） | `/exams/grade-writing` | 2 |
| AI Chat（AI 助手） | `/exams/ai-chat` | 1 |
| Translate（翻译） | `/exams/translate` | 1 |
| Cloze generate（完形填空生成） | `/words/cloze/generate` | 2 |
| Cloze check（完形填空批改） | `/words/cloze/check` | 1 |
| Word generate（词汇生成） | quick add / 词汇文件导入触发的 AI 词条解析 | 2 |
| Exam parse（普通试卷解析） | `/exams/upload`（普通模式） | 5 |
| Precise vision parse（精准视觉解析） | `/exams/upload`（精准模式） | 10 |
| Writing guidance（写作思路生成） | 写作题解析后的要点/思路补全 | 1 |
| Heading extract（标题列表抽取） | heading matching 后处理 fallback | 1 |

> cost 必须与 `AiFeature` 枚举的 `builtinCost` 保持一致；可后续按真实成本调整，调整时同步更新本文件与枚举。

---

## 3. 用户自填 API Key 模式（USER）

用户使用自己在用户中心配置的 API Key 调用 AI，**不消耗站点 credits**，但仍需基础保护。

### 3.1 保护规则

- **rate limit**：每用户每分钟请求数上限（建议初始 20 次/分钟，按接口可调）。
- **timeout**：单次 AI 调用超时上限（建议 60s，精准解析可放宽到 180s）。
- **输入长度限制**：
  - 文本任务：单次输入字符数上限（建议 20000 字符）。
  - 文件：复用现有上传大小限制（如 20MB）。
- **鉴权**：仍然必须登录，匿名不可用。
- **错误处理**：provider 返回 401/403（Key 失效）时给用户友好提示让其检查 Key，但不暴露原始响应。

---

## 4. 需要保护的接口列表

以下接口会触发 AI provider 调用，必须纳入保护：

| 路径 | 说明 |
|---|---|
| `/api/exams/grade-writing` | AI 写作评分 |
| `/api/exams/ai-chat` | AI 助手问答 |
| `/api/exams/translate` | AI 翻译 |
| `/api/exams/upload` | 试卷上传（普通 + 精准解析都会触发 AI） |
| `/api/words/cloze/generate` | 完形填空生成 |
| `/api/words/cloze/check` | 完形填空批改 |
| `/api/words/books/default/quick-add` | 默认词书快速添加单词，会异步触发 AI 词条生成 |
| `/api/words/books/{id}/quick-add` | 指定词书快速添加单词，会异步触发 AI 词条生成 |
| `/api/words/books/{id}/upload` | 词汇文件导入，解析后会异步触发 AI 词条生成 |
| 其他会触发 AI provider 调用的接口 | 新增 AI 接口时同步加入本表 |

> 精准解析（`parsePrecise=true`）走 Vision Provider，cost 更高（10）。
> 词汇文件导入会在 `AsyncWordService.processWordFile(...)` 中触发 `AiParseService.generateWordEntries(...)`，因此也应按 `WORD_GENERATE` 计入保护范围。
> 后续新增任何触发 AI 调用的接口，都必须：加鉴权 + 确定是否扣费 + 确定是否限流 + 加入本表。

---

## 5. 错误返回建议

| 场景 | HTTP 状态 | 行为 |
|---|---|---|
| 未登录 | 401 | Spring Security 统一处理 |
| credits 不足 | **429**（推荐）或业务错误码 | 返回"本周 AI 额度已用完，可切换自填 Key 模式" |
| 自填模式 rate limit 触发 | **429** | 返回"请求过于频繁，请稍后再试" |
| 自填 Key 失效 / 401 | 502 或业务错误码 | 返回"API Key 无效，请检查用户中心配置"，**不**返回 provider 原始错误 |
| provider 超时 | 504 | 返回"AI 服务响应超时，请稍后再试" |
| 输入超长 | 400 | 返回"输入内容过长" |
| provider 其他错误 | 502 | 返回通用"AI 服务暂时不可用"，**不**返回原始错误体 |

### 日志要求

- 后端日志**保留必要排查信息**：用户 ID、任务类型、provider、耗时、状态码、错误摘要。
- **脱敏**：日志中**不出现** API Key、`Authorization` 头、完整请求体中的 key 字段。
- provider 原始错误体可在 debug 级别记录脱敏后的版本，info 级别只记摘要。

---

## 6. 落地建议（后续阶段）

- credits 扣费应在 AI 调用**成功后**扣（避免失败也扣费）；或调用前预扣 + 失败回滚，二选一。
- rate limit 可复用现有 `infra/RedisOps`（Redis 可用时）或本地内存降级方案。
- 新增表 `ai_usage_quota`（或 `ai_usage_records`）记录额度与扣费流水，设计方向见 [database-change-guide.md](./database-change-guide.md)。

### 6.1 Phase 6A 已落地实现

- **扣费模型**：BUILTIN 采用「调用前预扣 + 失败回滚」。`AiUsageGuard.checkBeforeCall` 通过原子 `UPDATE ... SET credits_used = credits_used + cost WHERE credits_total - credits_used >= cost` 预扣，避免高并发下超卖；`markFailure` 用 `GREATEST(credits_used - cost, 0)` 回滚。`markSuccess` 不再扣费，仅写 SUCCESS 流水。
- **周期规则**：自然周（周一 00:00 ~ 下周一 00:00），默认每周 30 credits，quota 行按 `(user_id, period_start)` 唯一约束，并发插入冲突时回退查询。Quota creation defensively reloads the inserted row if the database/ORM does not populate the auto-increment ID back into the entity.
- **USER 模式限流**：本阶段使用**单机内存**滑动窗口（`ConcurrentHashMap` + 按分钟计数），每用户每 feature 每分钟 20 次，多实例不共享。Phase 6B-2C 起升级为 Redis 分布式限流（见 §6.5），内存限流保留为降级路径。
- **流水状态**：`SUCCESS` / `FAILED` / `REJECTED` 三种，`REJECTED` 用于额度不足（BUILTIN）或限流触发（USER），cost 均为 0。
- **errorMessage 脱敏**：移除 `Authorization: Bearer xxx` / `Bearer xxx` / `sk-xxx`，截断到 500 字符（对齐 `error_message VARCHAR(500)`）。
- **Phase 6B-2C 已落地**：USER 模式 Redis 分布式限流（见 §6.5）。管理端统计已在 Phase 6B-2B 落地（见 §6.4），Provider 字段记录已在 Phase 6B-2A 落地（见 §6.3）。

### 6.2 Phase 6B-1：额度查询与展示

- 用户可通过 `GET /api/users/me/ai-usage` 查询当前周期 credits 与最近 20 条使用记录。
- **查询接口只读**：不会创建 quota 行，也不会扣费；无 quota 时返回默认视图（creditsTotal=30, creditsUsed=0, creditsRemaining=30），避免用户只是打开页面就触发 quota 落库。
- userId 只能从 `@AuthenticationPrincipal AuthUser` 取，**不信任**前端传入的 userId。
- DTO 仅返回：`keyMode` / `periodStart` / `periodEnd` / `creditsTotal` / `creditsUsed` / `creditsRemaining` / `builtinQuotaEnabled` / `recentRecords`；record DTO 仅返回：`createdAt` / `taskType` / `feature` / `cost` / `keyMode` / `status` / `errorMessage`。**不返回** API Key / encrypted key / masked key / provider 原始 body。
- errorMessage 已由 `AiUsageGuard` 在写入时脱敏，查询接口不再加工。
- USER 模式不消耗站点 credits，但仍展示最近 usage records 与限流说明；视觉上弱化站点额度参考，避免用户误以为 USER 模式也会消耗 credits。
- 前端 `AiUsageCard.vue` 集成到 `ProfileView.vue` 的 `AiSettingsCard` 之后；不写入本地存储，不打印 payload。

### 6.3 Phase 6B-2A：Provider 记录

- `ai_usage_records.provider` 字段记录本次调用使用的 provider 枚举名，例如 `DEEPSEEK` / `QWEN` / `MIMO` / `OPENAI_COMPATIBLE`；当 provider 不可知时为 null。
- provider 字段**不包含** baseUrl、model、API Key 或 masked key；只写枚举名或 null，是结构安全的可统计维度。
- BUILTIN credits 不足（REJECTED）与 USER rate limit 触发（REJECTED）的流水也写 provider，方便后续统计拒绝来源。
- `AiUsageGuard` 旧三参数方法（`checkBeforeCall(userId, feature, keyMode)` / `markSuccess(...)` / `markFailure(..., ex)`）保留兼容，内部委托到 provider-aware overload 并传 `provider=null`；主 AI 调用点（`AiParseService` / `ClozeService` / `QwenAiParseService`）已全部迁移至新 overload。
- 用户中心 `AiUsageCard.vue` 最近记录表新增 Provider 列，枚举名映射为展示名（`DEEPSEEK→DeepSeek` / `QWEN→Qwen` / `MIMO→MiMO` / `OPENAI_COMPATIBLE→OpenAI-compatible` / null→`-`），未知 provider 原样展示枚举名。
- 该字段用于后续管理端统计与 provider 成功率分析，本阶段不做统计接口。

### 6.4 Phase 6B-2B：管理端统计

- ADMIN 可通过 `GET /api/admin/ai-usage/summary?days=7` 与 `GET /api/admin/ai-usage/recent?limit=50` 查看 AI usage 汇总统计与最近记录。
- 权限：`SecurityConfig` 加 `.requestMatchers("/admin/**").hasRole("ADMIN")` 路由级拦截；`AdminAiUsageController` 内 `requireAdmin(AuthUser)` 做防御性二次校验（不信任前端传 role，userId/role 只从 `@AuthenticationPrincipal AuthUser` 取）。
- 统计维度包括 status、provider、feature、keyMode、taskType、daily trend（缺失日期补 0）；`days` clamp 到 `[1,90]`，`limit` clamp 到 `[1,100]`。
- recent records 仅用于排查，errorMessage 已由 `AiUsageGuard` 在写入时脱敏。
- 统计 service 只读：不 insert / update / delete 任何数据；查询范围内全部 records 后在 Java 内 stream 聚合，避免写复杂 SQL。
- 前端 `views/admin/AdminAiUsageView.vue` 仅 ADMIN 可见入口（NavBar 条件渲染 + 路由守卫 `requiresAdmin`）；后端 `/admin/**` 仍会返回 403 兜底，前端展示「无权限」提示。
- 不返回 API Key / encrypted key / masked key / baseUrl / model / provider 原始响应体 / 用户密码 / 用户邮箱。

### 6.5 Phase 6B-2C：Redis 分布式限流

- USER 模式 rate limit 优先使用 Redis 固定窗口：`INCR ai:rate:user:{userId}:feature:{feature}:minute:{epochMinute}`，首次写入设置 TTL（70 秒，略大于 60 秒窗口确保过期清理）。
- Redis key 不包含 API Key、provider、baseUrl、model、prompt 或请求正文，仅含 userId / feature / epochMinute。
- Redis 不可用时降级到单机内存限流，保证 AI 功能可用，但多实例一致性依赖 Redis。
- `AiRedisRateLimiter` bean 通过 `@ConditionalOnProperty(app.redis.enabled=true)` 控制；关闭时 `AiUsageGuard` 经 `ObjectProvider` 拿不到该 bean，自动走内存路径。
- Redis 操作抛异常时由 `AiUsageGuard` 捕获并 fallback，**不向用户暴露** Redis 异常 message。
- 限流触发仍写 `ai_usage_records` 的 `REJECTED` 流水，cost=0，provider 字段保留。
- 不修改数据库表结构、不修改扣费策略、不新增依赖、不修改 BUILTIN 预扣/回滚逻辑。
