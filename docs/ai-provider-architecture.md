# AI Provider 架构设计

> 本文件是后续 AI Provider 改造的**设计文档**，**只写设计，不实现代码**。
> 当前阶段（Phase 1）不重构现有 Java/Vue 代码，不改依赖配置。
> 配套阅读：[security-and-quota-plan.md](./security-and-quota-plan.md)、[database-change-guide.md](./database-change-guide.md)。
>
> **实现进度：**
> - ✅ Phase 2：Provider 抽象与客户端骨架已落地（`AiTaskType` / `AiKeyMode` / `AiProviderPreset` / `AiCredentials` / `AiProviderRegistry` / `AiSettingsService` BUILTIN / `AiUsageGuard` 骨架）。
> - ✅ Phase 3A：`user_ai_settings` / `ai_usage_quota` / `ai_usage_records` 三表 + Entity/Mapper + `AiApiKeyCrypto` 已落地。
> - ✅ Phase 3B：用户 AI 设置后端接口已落地（`UserAiSettingsService` + `UserAiSettingsController` + DTO），`AiSettingsService` 已支持 USER 模式凭据解析；现有 AI 业务调用链尚未迁移（留待 Phase 5）。
> - ✅ Phase 3B-polish：安全与一致性补强。DTO 不再使用 `@Data`，`apiKey` 字段通过 `@ToString.Exclude` 排除；`AiSettingsService.resolveUser` 在解密前校验 provider 是否支持当前 `taskType`；`UserAiSettingsService` 公共方法加 `requireUserId` 防御。
> - ✅ Phase 4：前端用户中心设置 UI 已落地（`ProfileView.vue` 增加 `AiSettingsCard` AI 设置区，masked key 展示）。
> - ✅ Phase 5A：现有**文本类** AI 功能接入新架构。已迁移 `AiParseService` 中的 `gradeWriting` / `translateWithContext` / `chatWithContext` 三个方法，统一走 `AiSettingsService.resolve` → `AiUsageGuard.checkBeforeCall` → `OpenAiCompatibleClient.chat` → `markSuccess`/`markFailure`；`/exams/grade-writing` 已改为需要登录。
> - ✅ Phase 5A-polish: adjusted usage accounting so success is recorded only after provider response is successfully parsed/validated (避免返回非法 JSON / 空内容时同时记成功与失败、失败仍被扣费)。
> - ✅ Phase 5A-local-provider-test: Phase 5A also includes local OpenAI-compatible provider tests using JDK HttpServer, covering request serialization, token field handling, sanitized errors, and text-service integration without calling real providers.
> - ✅ Phase 5B：继续迁移剩余 TEXT Provider 功能。已迁移 `ClozeService.generate` / `ClozeService.check` / `AiParseService.generateWordEntries` 三个方法走新架构（`AiSettingsService.resolve(userId, TEXT)` → `AiUsageGuard.checkBeforeCall` → `OpenAiCompatibleClient.chat` → JSON 解析成功后 `markSuccess`，失败 `markFailure`）；新增 `AiFeature.WORD_GENERATE`（TEXT, cost=2）；`ClozeService` 与 `WordBookController` 接口改为从 `AuthUser` 注入 `userId`，禁止前端传 `userId`；`AsyncWordService` 两个调用点已下传 `userId`；provider 错误统一脱敏返回通用提示。
> - ✅ Phase 5C-1：普通文本试卷解析（`parsePrecise=false` 链路）接入新 Provider 架构。已迁移 `AiParseService.parseWithAi(Long userId, String)` / `detectAndParseMultiSection(Long userId, String)` / `workflowStep1` / `workflowStep1A` / `workflowStep1B` / `workflowStep2` 六个方法走 `AiFeature.EXAM_PARSE`（TEXT, cost=5）+ 新架构；`ExamService.uploadExam` / `uploadExamImages` 从 `AuthUser` 注入 `userId` 并下传给 `AsyncParseService.parseAndSave` / `parseAndSaveImages`，再透传到 `parseSingle` / `workflowParse` / `handleMultiSection` / `commitSection`（含 0-question 时的 `parseWithAi` retry）；`AiParseService.isConfigured()` 改为始终返回 `true`，避免 USER 模式被误判为未配置；未迁移的 `generateWritingGuidance` / `extractHeadingsWithAi` 改用 `hasLegacyDeepSeekKey()` 判断旧 DeepSeek client 是否可用；移除无调用方的 5-arg `parseAndSave` legacy 重载；provider 错误统一脱敏。新增 `AiParseServiceExamParseProviderTest`（6 用例）与 `AsyncParseServiceUserIdPropagationTest`（1 用例）。
> - ✅ Phase 5C-2：Vision Provider / Qwen-MiMO 精准解析（`parsePrecise=true` 链路）接入新架构。已迁移 `QwenAiParseService.parseDocument(Long userId, ...)` / `parseImages(Long userId, ...)` / `parseSingleImage` / `parseMultiPageImages` 走 `AiFeature.EXAM_PRECISE_PARSE`（VISION, cost=10）+ 新架构；新增统一 `callVisionProvider(userId, credentials, ...)` helper（`AiSettingsService.resolve(userId, VISION)` → `AiUsageGuard.checkBeforeCall` → `OpenAiCompatibleClient.chat` 多模态 `image_url` → `stripCodeFence` + `readJsonMap` + `normalizeParsedResult` 成功后 `markSuccess` / 任何异常 `markFailure`）；新增 `isConfigured(Long userId)` 由 `AiSettingsService.resolve(VISION)` 决定，旧无参 `isConfigured()` 标记 `@Deprecated` 返回 false；`AsyncParseService.parseAndSave` / `parseAndSaveImages` 精准路径改为 `isConfigured(userId)` + `parseDocument(userId, ...)` / `parseImages(userId, ...)`，并在 `parsePrecise=true` 但 Vision 未配置时抛清晰错误（"精准解析未配置，请前往个人中心配置 Vision Provider，或切换站点内置配置"）；移除旧 `callQwen` / `SHARED_HTTP_CLIENT` / `getActiveApiKey` / `getActiveBaseUrl` / `getActiveModel` / `getActiveMaxTokens` 及对应 `@Value` key/baseUrl/model 字段，保留 `aiMaxTokens` / `mimoMaxTokens` / `httpTimeoutSeconds` / PDF 渲染相关配置；provider 错误统一脱敏（`aiCallFailed` 只 log 异常类名，不 log message）；MIMO provider 自动使用 `max_completion_tokens` tokenField。新增 `QwenAiParseServiceProviderTest`（5 用例）、`QwenAiParseServiceLocalProviderTest`（1 用例，JDK HttpServer 端到端验证 `image_url` 多模态请求）与 `AsyncParseServiceVisionUserIdPropagationTest`（2 用例）。
> - ✅ Phase 5C-3：Legacy AI 调用清理。已迁移 `AiParseService.generateWritingGuidance(Long userId, ...)` 走 `AiFeature.WRITING_GUIDANCE`（TEXT, cost=1）+ 新架构（jsonMode=true, maxTokens=600, timeout=60s）；已迁移 `AiParseService.extractHeadingsWithAi(Long userId, ...)` 走 `AiFeature.HEADING_EXTRACT`（TEXT, cost=1）+ 新架构，失败时 `markFailure` + 返回 `Map.of()` 不抛异常（fallback 辅助不应破坏整份试卷解析）；新增 `postProcess(Long userId, ...)` overload 允许 heading AI fallback，旧 `postProcess(parsed, rawText)` 保留但仅做 regex / local fixes（`fixRangeOptions(null, ...)` → `getHeadingMap(null, ...)` 在 `userId==null` 时跳过 AI fallback）；`AsyncParseService` 4 个调用点（`handleQwenParsedResult` ×2 / `workflowParse` / `commitSection`）已改用带 userId 的版本；删除 `callDeepSeek` / `SHARED_HTTP_CLIENT` / 旧 HttpClient imports / 旧 DeepSeek `@Value` 字段（`apiKey` / `baseUrl` / `model` / `maxTokens` / `textMaxChars`）/ `hasLegacyDeepSeekKey()`；删除死代码 `QwenDocumentParseService.java`（无调用方）；`LlamaParseService` 仍为死代码，TODO 标记为 `future cleanup`（Phase 5C-3 未纳入）。生产 AI 调用统一走 `AiSettingsService + AiUsageGuard + OpenAiCompatibleClient`。新增 `AiParseServiceLegacyCleanupTest`（5 用例）与 `AiFeatureTest` 2 用例。
>   - 仍未迁移（备选链路，当前为死代码）：`LlamaParseService`（备选解析链路未启用，无调用方，TODO 标记为 `future cleanup`）。
> - ✅ Phase 6A：`AiUsageGuard` 后端额度与流水落地。BUILTIN 每周 30 credits + 预扣（原子 `UPDATE ... WHERE credits_total - credits_used >= cost`）+ 失败回滚（`GREATEST(credits_used - cost, 0)`）；USER 模式本机内存滑动窗口限流（每用户每 feature 每分钟 20 次）；`ai_usage_records` 写 SUCCESS / FAILED / REJECTED 流水；errorMessage 脱敏（`Authorization: Bearer xxx` / `Bearer xxx` / `sk-xxx` 前缀替换为 `[REDACTED]` + 截断到 500 字符）。
> - ✅ Phase 6A-polish：`AiUsageGuard.getOrCreateCurrentQuota` 在 `insert` 成功但 ID 未回填到 entity 时，defensively reload 当前周期 quota 行再走预扣；新增 `builtinShouldReloadQuotaWhenInsertedIdNotReturned` 与 `builtinShouldThrowWhenInsertedQuotaCannotBeReloaded` 两个测试锁住行为。
> - ✅ Phase 6B-1：AI 用量查询接口与用户中心额度展示。后端新增 `GET /api/users/me/ai-usage`（`UserAiUsageController` + `AiUsageQueryService`，只读不创建 quota 行、不扣费，无 quota 时返回默认视图 30/0/30）；新增 `UserAiUsageDto` / `AiUsageRecordDto`；userId 只能从 `@AuthenticationPrincipal AuthUser` 取；最近 20 条 records 按 createdAt 倒序返回。前端新增 `api/aiUsage.js` + `components/profile/AiUsageCard.vue`（BUILTIN 模式展示本周额度/进度条/周期/最近 20 条 records；USER 模式展示「不消耗站点额度」提示 + 弱化站点额度参考 + records；feature 中文映射；刷新按钮；错误降级提示），并集成到 `ProfileView.vue` 的 `AiSettingsCard` 之后。新增 `AiUsageQueryServiceTest`（6 用例）覆盖默认视图、现有 quota、USER 模式、records 顺序、不创建 quota、null userId 拒绝。
> - ✅ Phase 6B-2A：`ai_usage_records.provider` 字段落地。`AiUsageGuard` 新增 provider-aware overload（`checkBeforeCall(userId, feature, keyMode, provider)` / `markSuccess(...)` / `markFailure(..., provider, ex)`），旧三参数方法保留兼容并委托到新方法传 `provider=null`；`insertRecord` private helper 新增 `provider` 参数，`record.setProvider(provider)` 写入枚举名（`DEEPSEEK` / `QWEN` / `MIMO` / `OPENAI_COMPATIBLE` 或 null），不包含 baseUrl/model/API Key；BUILTIN credits 不足与 USER rate limit 触发的 REJECTED 流水也写 provider；`AiParseService` / `ClozeService` / `QwenAiParseService` 三个 service 共 45 个 AI 调用点迁移至 provider-aware overload（统一通过 `providerName(credentials)` helper 取 `credentials.getProvider().name()`）；`AiUsageRecordDto` 新增 `provider` 字段并在 `AiUsageQueryService.toDto` 透传；前端 `AiUsageCard.vue` records 表新增 Provider 列（`DEEPSEEK→DeepSeek` / `QWEN→Qwen` / `MIMO→MiMO` / `OPENAI_COMPATIBLE→OpenAI-compatible` / null→`-`）。新增 `AiUsageGuardTest` 5 个 provider 用例覆盖 REJECTED-BUILTIN / REJECTED-USER / SUCCESS / FAILED / 旧方法 null；更新 8 个已有 service 测试的 `verify(aiUsageGuard)` 调用以匹配新 4/5 参数签名；`AiUsageQueryServiceTest` 新增 provider 透传断言。后端 `mvn test` 184 用例通过，前端 `npm run build` 通过。
> - ✅ Phase 6B-2B：管理端 AI usage 统计接口与统计面板。后端新增 `GET /api/admin/ai-usage/summary?days=7`（`AdminAiUsageController` + `AdminAiUsageStatsService`，按 status / provider / feature / keyMode / taskType 聚合 + daily trend 补 0 + totalCalls/success/failed/rejected/totalCost/uniqueUsers 汇总；`days` clamp 到 `[1,90]`）与 `GET /api/admin/ai-usage/recent?limit=50`（`limit` clamp 到 `[1,100]`，按 createdAt 倒序）；权限：`SecurityConfig` 加 `.requestMatchers("/admin/**").hasRole("ADMIN")` + Controller 内 `requireAdmin(AuthUser)` 防御性二次校验；新增 `AdminAiUsageSummaryDto` / `AdminAiUsageBucketDto` / `AdminAiUsageRecentRecordDto`；null bucket 归 `UNKNOWN`；只读 service，不 insert/update/delete。前端新增 `api/adminAiUsage.js` + `views/admin/AdminAiUsageView.vue`（顶部统计卡片 + 5 个分组表 + daily trend 表 + 最近记录表，复用 AiUsageCard 的 FEATURE/PROVIDER 映射）；`router/index.js` 加 `/admin/ai-usage` 路由 + `requiresAdmin` 守卫；`stores/auth.js` 暴露 `isAdmin` computed；`NavBar.vue` 在用户下拉与移动端菜单加 ADMIN 可见入口。不返回 API Key / encrypted key / masked key / baseUrl / model。新增 `AdminAiUsageStatsServiceTest` 5 用例覆盖多维度聚合 / days clamp / dailyTrend 补 0 / recent clamp+顺序 / null bucket 归 UNKNOWN。后端 `mvn test` 189 用例通过，前端 `npm run build` 通过。
> - ✅ Phase 7A：Release hardening / 发布前验收。运行全量后端 `mvn test`（189 用例通过）与前端 `npm run build`（通过）；安全敏感字段 grep 分类确认（`apiKey` / `api_key` / `encrypted` / `masked` / `Authorization` / `Bearer` / `sk-` / `callDeepSeek` / `SHARED_HTTP_CLIENT` / `QwenDocumentParseService` / `qwen.api-key` 等均落在允许区域：加密工具、DTO masked 展示、tests fake key、docs 安全说明、`OpenAiCompatibleClient` 统一 HTTP 调用、application config placeholder；前端 localStorage 仅存 JWT/user/theme/exam history/error book/word progress，无 API Key）；旧 direct provider 调用残留检查（`callDeepSeek` / `QwenDocumentParseService` 已删除，`SHARED_HTTP_CLIENT` 仅存在于 `OpenAiCompatibleClient` 统一客户端与死代码 `LlamaParseService`，生产 service 不再绕过 `OpenAiCompatibleClient`）；权限矩阵检查（用户 AI settings/usage 接口均从 `@AuthenticationPrincipal AuthUser` 取 userId，不信任前端；`/admin/**` hasRole ADMIN + Controller 二次校验；AI 功能接口经 `AiUsageGuard.checkBeforeCall` 预扣/限流 + `markSuccess`/`markFailure`）；DB schema / Entity 一致性（`user_ai_settings` / `ai_usage_quota` / `ai_usage_records` 三表字段与 Entity 完全对齐，`(user_id, period_start)` 唯一约束存在，provider 字段已落地）；Redis 开关路径检查并修复 blocker：`app.redis.enabled` 默认值由 `true` 改为 `false`，使「不配置时」`AiRedisRateLimiter` bean 不创建、`AiUsageGuard` 走内存限流，与设计注释和任务 §八 要求一致；`enabled=true` 时 `AiRedisRateLimiter` 使用 `StringRedisTemplate`，Redis 异常 fallback 到内存限流，不向用户暴露 Redis 异常 message，REJECTED 流水仍写 provider；前端检查（API Key input 不回填 raw key，masked key 仅展示 masked，usage/admin usage 不写 localStorage/sessionStorage，不 console.log payload，admin route 有 `requiresAdmin`，NavBar 仅对 ADMIN 显示管理入口）。新增 `docs/ai-provider-smoke-test.md`（8 节可手动执行的 smoke test checklist）。未修改数据库表结构、未修改扣费策略、未修改 AI prompt、未修改 provider request/response 结构、未改依赖版本/lock 文件、未真实请求外部 AI Provider。
> - ✅ Phase 7B：配置与部署文档收口。`application.yml` 全部生产敏感字段改为 `${ENV_VAR:dev-default}` 占位：`SERVER_PORT` / `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` / `SPRING_DATA_REDIS_HOST/PORT/PASSWORD/DATABASE` / `JWT_SECRET` / `MINIO_*`；`application-ai.yml` 改为 `DEEPSEEK_API_KEY/BASE_URL/MODEL` / `AI_PRECISE_PROVIDER` / `MIMO_API_KEY/BASE_URL/MODEL` / `QWEN_API_KEY/BASE_URL/MODEL` 环境变量覆盖（`AI_KEY_ENCRYPTION_SECRET` 已在 7A 前支持）；`SecurityConfig` 新增 `@Value("${app.cors.allowed-origins:}")` 字段，`corsConfigurationSource()` 在 `CORS_ALLOWED_ORIGINS` 非空时按逗号分隔注入实际前端域名，留空时仅允许 localhost/127.0.0.1/[::1]（本地开发不破坏）；新增根目录 `.env.example`（后端 + 前端统一模板，所有 key 留空或 `change-me`）；新增 `docs/deployment-config.md`（9 节：必填/可选变量、AI 内置 key、加密密钥注意事项、Redis 限流开关、DB 初始化、前端变量、生产安全 checklist、本地开发示例）；`.gitignore` 补充根目录 `.env` / `.env.local` / `.env.production` 忽略规则；`README.md` 在「环境变量说明」后增加 Deployment configuration 小节指向新文档。未改业务逻辑、未改 DB 结构、未改 AI Provider 调用链、未改扣费策略、未改 Redis 限流算法、未改依赖版本/lock 文件、未提交真实 key/password/secret。`mvn test` 189 用例通过，`npm run build` 通过。
> - ✅ Phase 8A：Admin 用户管理后台。新增 `AdminUserController`（`/admin/users`：列表/详情/修改角色/禁用/启用/重置密码 6 个接口）+ `AdminUserService` + 4 个 DTO（`AdminUserDto` / `AdminUserPageDto` / `AdminUpdateUserRoleRequest` / `AdminResetPasswordRequest`，均不含 password 字段）；`UserMapper` 扩展 7 个自定义 SQL 方法（`selectAdminUsers` / `countAdminUsers` / `selectUserIncludingDeleted` / `updateDeletedById` / `updateRoleById` / `updatePasswordById` / `countActiveAdmins`）绕过 `@TableLogic` 以查询/操作已禁用用户，不破坏现有普通用户查询语义。安全：`/admin/**` hasRole ADMIN + Controller `requireAdmin` 二次校验；不能禁用/降级自己、不能禁用/降级最后一个 ADMIN；重置密码使用 `PasswordEncoder.encode` BCrypt 哈希，`AdminResetPasswordRequest.newPassword` 通过 `@ToString.Exclude` 排除日志泄露；DTO 永不返回 password；userId/role 只从 `@AuthenticationPrincipal AuthUser` 取。前端新增 `api/adminUsers.js` + `views/admin/AdminUsersView.vue`（keyword 搜索 + role/status 筛选 + 分页 + 修改角色 Dialog + 禁用/启用确认 + 重置密码 Dialog，不存密码到本地存储、不打印密码）；`router/index.js` 加 `/admin/users` 路由 + `requiresAdmin` 守卫；`NavBar.vue` 在用户下拉与移动端菜单加 ADMIN 可见「用户管理」入口。未改 DB 结构、未改 AI Provider / quota / rate limit 逻辑、未改登录注册主流程、未改 JWT 结构、未新增依赖。新增 `AdminUserServiceTest`（10 用例）覆盖 clamp / 过滤 / 角色保护 / 禁用保护 / 启用 / 重置密码 / DTO 不暴露 password。后端 `mvn test` 207 用例通过，前端 `npm run build` 通过。
> - ✅ Phase 8C：Admin 轻量权限增强。保留 USER/ADMIN 两级基础角色不变，新增 `admin_user_permissions` 表（`(user_id, permission)` 唯一约束）+ `AdminPermission` 枚举（7 值：`ADMIN_USERS_VIEW` / `ADMIN_USERS_MANAGE` / `ADMIN_USERS_RESET_PASSWORD` / `ADMIN_AI_USAGE_VIEW` / `ADMIN_QUOTA_VIEW` / `ADMIN_QUOTA_MANAGE` / `ADMIN_PERMISSIONS_MANAGE`）+ `AdminUserPermission` entity + `AdminUserPermissionMapper`（5 自定义方法：`selectByUserId` / `countAllPermissions` / `countUsersWithPermission` / `deleteByUserId` / `deleteByUserIdAndPermission`）+ `AdminPermissionService`（含 `isExplicitPermissionMode` / `getUserPermissions` / `hasPermission` / `requirePermission` / `buildPermissionDto` / `updateUserPermissions` 与 5 条安全规则）+ `AdminPermissionController`（`/admin/permissions`：`GET /` list all、`GET /me` my permissions、`GET /users/{userId}` get user permissions、`PUT /users/{userId}` update user permissions）+ 2 个 DTO（`AdminPermissionDto` / `AdminUpdatePermissionsRequest`，均不含 password/apiKey/encrypted/masked/baseUrl/model）。兼容策略：表为空 → 所有 ADMIN 拥有全部权限；表非空 → 显式权限模式。安全规则：① 只有 ADMIN 可以拥有权限 ② 不能移除自己的 `ADMIN_PERMISSIONS_MANAGE` ③ 不能移除系统中最后一个 `ADMIN_PERMISSIONS_MANAGE` ④ 显式模式启动后必须至少保留一个 `ADMIN_PERMISSIONS_MANAGE` ⑤ 权限白名单。`AdminUserController`（7 接口）+ `AdminAiUsageController`（2 接口）+ `AdminQuotaController`（5 接口）共 14 个已有 Admin 接口加 `requireAdmin(authUser, AdminPermission.XXX)` 精细权限二次校验（兼容模式下不限制）。前端新增 `api/adminPermissions.js` + `stores/auth.js` 加 `adminPermissions` ref + `loadAdminPermissions()` 懒加载 + `hasAdminPermission(perm)` helper（logout 清空）；`NavBar.vue` 按权限显示后台菜单（mounted 懒加载）；`AdminUsersView.vue` 操作列加「权限」按钮（仅当前 ADMIN 持有 `ADMIN_PERMISSIONS_MANAGE` 时显示）+ 权限配置 Dialog（7 checkbox + 显式模式状态 + 安全规则提示）。新增 `AdminPermissionServiceTest`（12 用例）覆盖显式/兼容模式、USER 无权限、5 条安全规则、成功路径先删后插、`requirePermission` 抛 `AccessDeniedException`、DTO 不暴露敏感字段。未改 AI Provider 调用链 / quota 扣费 / rate limit / JWT 结构 / 登录注册主流程 / 依赖版本。后端 `mvn test` 通过，前端 `npm run build` 通过。

---

## 1. 当前现状

| 任务类型 | 主要 Service | Provider | 说明 |
|---|---|---|---|
| 文本试卷解析 | `AiParseService` | DeepSeek | 普通 PDF / Word 文字结构化 |
| 写作评分 | `AiParseService` | DeepSeek | TR / CC / LR / GRA + Band 分 |
| AI 助手问答 | `AiParseService` | DeepSeek | 考试中上下文问答 |
| 划词翻译 | `AiParseService` | DeepSeek | 翻译 + 语法注释 |
| 完形填空生成/批改 | `ClozeService`（依赖 AI） | DeepSeek | 文本任务 |
| 多模态精准解析 | `QwenAiParseService` | Qwen VL / MiMO | PDF 逐页渲染为图片后视觉识别 |
| 备选解析 | `LlamaParseService` | LlamaParse | 备选方案 |

**问题：**

- AI Key 由站点统一配置（`application-ai.yml`），用户无法自带 Key。
- 文本与多模态调用散落在不同 Service，没有统一抽象。
- 没有用户级 quota 与 rate limit。
- 用户无法选择 Provider 或自定义 Base URL / Model。

---

## 2. 目标架构

### 2.1 任务类型枚举

```java
public enum AiTaskType {
    TEXT,   // 文本任务：写作评分、AI Chat、翻译、普通解析、完形填空
    VISION  // 多模态任务：PDF/图片精准解析、扫描版试卷、写作 Task 1 图表识别
}
```

### 2.2 Key 模式枚举

```java
public enum AiKeyMode {
    BUILTIN, // 使用站点内置 API Key，消耗站点 credits
    USER     // 使用用户自填 API Key，不消耗站点 credits
}
```

### 2.3 核心抽象

| 组件 | 职责 |
|---|---|
| `AiProviderPreset` | 预设 Provider 枚举/配置（DeepSeek、Qwen、MiMO 等），含默认 Base URL / Model |
| `AiCredentials` | 一次 AI 调用所需的凭据快照（provider、baseUrl、model、apiKey 明文-内存、keyMode），由 `AiSettingsService` 解密后注入 |
| `OpenAiCompatibleClient` | 统一 HTTP 客户端，封装 OpenAI-compatible `/chat/completions`，支持 text 与 vision（image_url）消息 |
| `AiSettingsService` | 读取用户 AI 设置（`user_ai_settings`），决定 Key 模式、解密 API Key、构造 `AiCredentials` |
| `AiUsageGuard` | 用量守卫：内置模式校验 credits 余量并扣费；自填模式做 rate limit / timeout / 输入长度校验 |

### 2.4 调用流程（设计）

```
Controller
   │  @AuthenticationPrincipal AuthUser
   ▼
SomeService
   │  AiTaskType.TEXT / VISION
   ▼
AiSettingsService.resolve(userId, taskType) → AiCredentials
   │  (BUILTIN: 站点 Key；USER: 解密用户 Key)
   ▼
AiUsageGuard.checkAndConsume(userId, taskType, keyMode, cost)
   │  (BUILTIN: 扣 credits；USER: rate limit)
   ▼
OpenAiCompatibleClient.chat(credentials, messages) → result
   │  (异常脱敏后抛出)
   ▼
SomeService 组装业务结果 → Result.success(...)
```

---

## 3. Provider 分类

### 3.1 Text Provider

| Provider | 说明 |
|---|---|
| DeepSeek | 默认文本 Provider，性价比高，写作评分/解析/翻译/Chat |
| OpenAI-compatible custom | 用户自填 Base URL / Model / API Key，走 `/chat/completions` |

### 3.2 Vision Provider

| Provider | 说明 |
|---|---|
| Qwen | 通义千问 VL，PDF/图片精准解析、扫描版试卷 |
| MiMO | 多模态解析备选 |
| OpenAI-compatible custom | 用户自填支持 vision 的 Base URL / Model / API Key |

> 约定：所有 Provider 尽量走 OpenAI-compatible `/chat/completions` 协议；多模态用 `image_url` 消息体。
> 非 OpenAI-compatible 的 Provider（如有）由 `OpenAiCompatibleClient` 适配层屏蔽差异，业务层不感知。

---

## 4. 用户设置模型

一个用户有一份 AI 设置（`user_ai_settings` 一对一）：

- **大开关**：`key_mode = BUILTIN | USER`
- **BUILTIN 模式**：无需配置 Provider，直接用站点内置 Key，消耗 credits
- **USER 模式**：分别配置文本任务和多模态任务
  - Text：`text_provider` + `text_base_url` + `text_model` + `text_api_key_encrypted` + `text_api_key_masked`
  - Vision：`vision_provider` + `vision_base_url` + `vision_model` + `vision_api_key_encrypted` + `vision_api_key_masked`

字段设计详见 [database-change-guide.md](./database-change-guide.md)。

---

## 5. 安全规则

- **API Key 加密存储**：`*_api_key_encrypted` 字段使用可逆加密（如 AES），密钥由后端持有，**不**入库。
- **前端只展示 masked key**：`*_api_key_masked`（如 `sk-****abcd`），**永不**返回明文。
- **日志禁止输出 key**：打印请求时去掉 `Authorization` 头与 body 中的 key；打印 provider 响应时只保留必要字段。
- **自定义 Base URL 校验**：
  - 必须 `http`/`https` 协议。
  - host 不允许指向内网/保留地址（防 SSRF），除非显式配置白名单。
  - 校验失败返回明确业务错误，不直接转发请求。
- **错误脱敏**：provider 原始异常不完整返回前端，后端记录脱敏后的日志。

详细安全策略见 [security-and-quota-plan.md](./security-and-quota-plan.md)。

---

## 6. 后续实施阶段

| 阶段 | 状态 | 内容 |
|---|---|---|
| Phase 1 | ✅ 已完成 | Agent 文档与开发基础设施（`AGENTS.md`、`docs/*`） |
| Phase 2 | ✅ 已完成 | Provider 抽象与 OpenAI-compatible client：`AiTaskType` / `AiKeyMode` / `AiProviderPreset` / `AiCredentials` / `AiProviderRegistry` / `AiSettingsService` BUILTIN / `AiUsageGuard` 骨架 |
| Phase 3A | ✅ 已完成 | 数据库表、Entity/Mapper、API Key 加密工具（`init.sql`、`entity/`、`mapper/`、`AiApiKeyCrypto`） |
| Phase 3B | ✅ 已完成 | 用户 AI 设置后端接口与 USER 模式 credentials 解析（`UserAiSettingsService` / `UserAiSettingsController` / DTO，`AiSettingsService` 已支持 USER 模式） |
| Phase 3B-polish | ✅ 已完成 | 安全补强：DTO `toString()` 防泄露、`resolveUser` 解密前校验 provider-taskType、`requireUserId` 防御 |
| Phase 4 | ✅ 已完成 | 前端用户中心 AI 设置 UI：在 `ProfileView.vue` 增加 AI 设置区（`AiSettingsCard.vue`），masked key 展示（`frontend/src/views/ProfileView.vue`、`frontend/src/components/profile/`、`frontend/src/api/aiSettings.js`） |
| Phase 5A | ✅ 已完成 | 现有**文本类** AI 功能接入新架构：迁移 `AiParseService.gradeWriting` / `translateWithContext` / `chatWithContext` 走 `AiSettingsService` + `AiUsageGuard` + `OpenAiCompatibleClient`；`/exams/grade-writing` 改为需要登录 |
| Phase 5B | ✅ 已完成 | 继续迁移 TEXT Provider 功能：`ClozeService.generate` / `ClozeService.check` / `AiParseService.generateWordEntries` 走新架构；新增 `AiFeature.WORD_GENERATE`；`/words/cloze/*` 接口从 `AuthUser` 注入 `userId`；`AsyncWordService` 调用点下传 `userId` |
| Phase 5C-1 | ✅ 已完成 | 普通文本试卷解析（`parsePrecise=false`）接入新架构：`AiParseService.parseWithAi` / `detectAndParseMultiSection` / `workflowStep1` / `workflowStep1A` / `workflowStep1B` / `workflowStep2` 走 `AiFeature.EXAM_PARSE`；`ExamService` → `AsyncParseService` 一路下传 `userId`（`parseAndSave` / `parseAndSaveImages` / `parseSingle` / `workflowParse` / `handleMultiSection` / `commitSection`）；`isConfigured()` 改为 `return true`；移除 5-arg legacy `parseAndSave` |
| Phase 5C-2 | ✅ 已完成 | PDF/图片精准解析（`parsePrecise=true`）接入新架构：`QwenAiParseService.parseDocument(userId, ...)` / `parseImages(userId, ...)` 走 `AiSettingsService.resolve(userId, VISION)` + `AiUsageGuard` + `OpenAiCompatibleClient`；使用 `AiFeature.EXAM_PRECISE_PARSE`；支持 Qwen/MiMO/custom OpenAI-compatible Vision Provider；`AsyncParseService` precise path 已下传 userId；已补 Vision local provider tests |
| Phase 5C-3 | ✅ 已完成 | Legacy AI 调用清理：`generateWritingGuidance` / `extractHeadingsWithAi` 迁移至新架构（`AiFeature.WRITING_GUIDANCE` / `HEADING_EXTRACT`）；新增 `postProcess(Long userId, ...)` overload；删除 `callDeepSeek` / `SHARED_HTTP_CLIENT` / 旧 DeepSeek `@Value` 字段 / `hasLegacyDeepSeekKey()`；删除死代码 `QwenDocumentParseService.java`；生产 AI 调用统一走 `AiSettingsService + AiUsageGuard + OpenAiCompatibleClient` |
| Phase 6A | ✅ 已完成 | `AiUsageGuard` 后端额度与流水落地：BUILTIN 每周 30 credits、预扣+失败回滚（原子 `UPDATE ... WHERE credits_total - credits_used >= cost`）、USER 内存 rate limit（每用户每 feature 每分钟 20 次）、`ai_usage_records` 写 SUCCESS / FAILED / REJECTED 流水、errorMessage 脱敏（Authorization/Bearer/sk- 前缀 + 截断 500） |
| Phase 6B-1 | ✅ 已完成 | AI usage 查询接口与用户中心额度展示：`GET /api/users/me/ai-usage`，展示当前周期 credits、keyMode、最近 usage records |
| Phase 6B-2A | ✅ 已完成 | `ai_usage_records.provider` 字段落地：`AiUsageGuard` provider-aware overload，主 AI 调用点写入 provider，用户中心最近记录展示 provider |
| Phase 6B-2B | ✅ 已完成 | 管理端 AI usage 统计接口与统计面板：按 status / provider / feature / keyMode / taskType / daily trend 聚合，支持最近 records 查看 |
| Phase 6B-2C | ✅ 已完成 | USER 模式 Redis 分布式 rate limit：优先 Redis INCR + TTL 固定窗口，Redis 不可用时降级到单机内存限流 |
| Phase 7A | ✅ 已完成 | Release hardening：全量测试、敏感字段 grep、权限矩阵、DB/Entity 一致性、Redis 开关路径、前端 build、smoke test checklist |
| Phase 7B | ✅ 已完成 | 配置与部署文档收口：`application.yml` / `application-ai.yml` 全部生产敏感字段改为 `${ENV_VAR:dev-default}` 占位（DB_URL/DB_USERNAME/DB_PASSWORD/JWT_SECRET/Redis host/port/password/database/SERVER_PORT/DEEPSEEK_*/MIMO_*/QWEN_*/AI_PRECISE_PROVIDER/AI_KEY_ENCRYPTION_SECRET/MINIO_*）；`SecurityConfig` 新增 `CORS_ALLOWED_ORIGINS` 环境变量覆盖（留空时仅允许 localhost，生产必填实际前端域名）；新增根目录 `.env.example`、`docs/deployment-config.md`（9 节：必填/可选变量、AI 内置 key、加密密钥、Redis 限流、DB 初始化、前端变量、生产安全 checklist、本地开发示例）；`.gitignore` 补充根目录 `.env` 忽略规则；`README.md` 增加 Deployment configuration 小节。未改业务逻辑、未改 DB 结构、未改 AI Provider 调用链、未改依赖版本、未提交真实 key。`mvn test` 189 用例通过，`npm run build` 通过 |
| Phase 7C | ✅ 已完成 | GitHub Actions CI 与交付检查：新增 `.github/workflows/ci.yml`（3 个 job：`backend-test` 跑 `mvn -B test` + `package -DskipTests` 并上传 jar artifact；`frontend-build` 跑 `npm ci` + `npm run build` 并上传 dist artifact；`security-grep` 检查 `callDeepSeek`/`QwenDocumentParseService` 旧代码残留、`qwen.api-key`/`mimo.api-key`/`deepseek.api-key` 生产代码硬编码、前端 `localStorage`/`sessionStorage` 存 apiKey、AI 相关前端代码 `console.log(` 调用）；触发条件 push main/master/develop + pull_request；Java 21 / Node 20 / Maven cache / npm cache；artifact 保存 7 天，非正式 Release；新增 `docs/release-checklist.md`（10 节：CI checks、artifacts、部署责任边界、git pull 部署、CI artifact 部署、DB 迁移、环境变量、Redis 模式、smoke test、回滚说明）；`README.md` 增加 CI and release checklist 小节。未做自动部署、未新增 Secrets、未新增 Docker、未创建 GitHub Release、未提交真实 key、未改业务逻辑/DB 结构/扣费策略/Provider 调用链。本地 `mvn test` 通过、`npm run build` 通过 |
| Phase 8A | ✅ 已完成 | Admin 用户管理后台：用户列表/搜索、角色修改、禁用/启用、重置密码，保留 USER/ADMIN 两级权限 |
| Phase 8B | ✅ 已完成 | Admin AI 额度管理后台：查看当前周期 quota、设置总额度、增加额度、重置已用额度（无 quota 行时读操作返回默认视图不创建、写操作创建当前周期 quota 行；不修改 AI Provider / 扣费 / rate limit / usage records 逻辑；未改 DB 结构） |
| Phase 8C | ✅ 已完成 | Admin 轻量权限增强：保留 USER/ADMIN 基础角色，新增 admin_user_permissions 表与后台权限分配，支持不同 ADMIN 拥有不同管理权限 |
| Phase 8D | ✅ 已完成 | Admin 操作审计日志：记录管理端高风险写操作（创建/修改/禁用/启用/重置密码/quota/权限），支持按 actor/action/resource/status/time 查询，summary 脱敏不含 password/API Key/token/Bearer/sk-* values；新增 ADMIN_AUDIT_LOG_VIEW 权限与前端审计日志页面 |
| Phase 9A | ✅ 已完成 | 普通环境部署与本地调试 Runbook：Java 21 / MySQL / Redis 可选 / Node + Vite，覆盖本地启动、生产构建、手动部署、CORS、Smoke Test 与常见问题 |

> 各阶段应独立 PR，小步推进，每阶段都要跑通验证命令（`mvn test` / `npm run build`）。
