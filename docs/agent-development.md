# Agent 开发指引

> 本文件是面向 AI coding agent 的详细开发指引，配合根目录 [AGENTS.md](../AGENTS.md) 使用。
> 目的：让任何 agent 接手任务时都能按统一路径操作，减少破坏性改动。

---

## 1. 开发前检查清单

- [ ] 已阅读 [AGENTS.md](../AGENTS.md)，了解项目结构与分层约定。
- [ ] 已阅读本文件相关章节。
- [ ] 已确认任务范围（本次只做一件事，不混入无关改动）。
- [ ] 已用 Read / Grep / SearchCodebase 阅读相关现有代码，**不凭猜测修改**。
- [ ] 涉及数据库改动：已阅读 [database-change-guide.md](./database-change-guide.md) 并查看 `backend/src/main/resources/db/init.sql`。
- [ ] 涉及 AI 调用：已阅读 [ai-provider-architecture.md](./ai-provider-architecture.md) 与 [security-and-quota-plan.md](./security-and-quota-plan.md)。
- [ ] 涉及前端：已确认改动落在 `frontend/src/api/`、`stores/`、`views/`、`components/` 的正确位置。
- [ ] 不打算改依赖管理（`pom.xml` 依赖坐标/版本、`package.json` 依赖版本、lock 文件）。

---

## 2. 修改后检查清单

- [ ] 后端：`cd backend && mvn test`（无测试则 `mvn compile`）通过。
- [ ] 前端：`cd frontend && npm run build` 通过。
- [ ] 新增接口已加鉴权（`@AuthenticationPrincipal AuthUser` 或在 `SecurityConfig` 中确认非 `permitAll`）。
- [ ] 新增接口返回 `Result<T>`，未绕过统一返回。
- [ ] Controller 中没有出现复杂业务逻辑、Prompt 拼接、直接 HTTP 调用。
- [ ] AI 调用未把 API Key 写进日志或返回前端。
- [ ] 数据库改动已同步 `init.sql`，并写了 migration note。
- [ ] 没有提交真实密钥、密码、JWT secret。
- [ ] diff 聚焦，没有大面积格式化无关文件，没有混入无关功能。

---

## 3. 后端常见修改路径

### 3.1 新增一个业务接口（标准流程）

1. **Entity**：若涉及新表，在 `entity/` 下新建实体类（`@TableName`、`@TableId`、字段 `@TableField`）。
2. **Mapper**：在 `mapper/` 下新建 Mapper 接口继承 `BaseMapper<T>`。
3. **DTO**：在 `dto/` 下新建请求/响应 DTO，使用 `jakarta.validation` 注解。
4. **Service**：在 `service/` 下新建 Service（接口 + Impl 或直接类），写业务逻辑，AI 调用在这里。
5. **Controller**：在 `controller/` 下新建或扩展 Controller，`@RestController` + `@RequestMapping`，方法签名用 `@AuthenticationPrincipal AuthUser authUser` 注入用户，返回 `Result.success(...)`。
6. **SecurityConfig**：确认新接口路径的鉴权策略（默认全部需要登录）。
7. **init.sql**：若新建了表，更新 `init.sql`。

### 3.2 修改 AI 调用

- 所有 AI 调用必须落在 `service/` 层，并通过 `AiSettingsService` → `AiUsageGuard` → `OpenAiCompatibleClient` 统一抽象（已落地，详见 [ai-provider-architecture.md](./ai-provider-architecture.md)）。
- **不要**在 Controller 里拼 Prompt 或直接 `RestTemplate`/`WebClient` 调 provider；也**不要**新增 direct `HttpClient` 调用绕过 `OpenAiCompatibleClient`。
- 生产 service 不得复活旧的 `callDeepSeek` / `QwenDocumentParseService` / `SHARED_HTTP_CLIENT` 直连写法（CI 已加 grep 拦截）。

### 3.3 修改配置

- 通用配置改 `backend/src/main/resources/application.yml`。
- AI 相关配置改 `backend/src/main/resources/application-ai.yml`。
- **生产敏感配置（DB 密码、API Key、JWT secret、AI 加密密钥等）一律通过环境变量注入**，不要硬编码进 yml。`application.yml` 已用 `${ENV_VAR:dev-default}` 占位，留空时走开发默认值。
- 本地敏感配置推荐放在根目录 `.env`（复制 `.env.example` 而来，已 `.gitignore`），`application.yml` 通过 `spring.config.import: optional:file:../.env[.properties]` 自动加载，从 `backend/` 运行 `mvn spring-boot:run` 即可，无需 profile。
- 兼容旧方式：也可继续使用 `backend/src/main/resources/application-local.yml`（已 `.gitignore`）+ `mvn spring-boot:run -Dspring-boot.run.profiles=local`，但 `.env` 是新推荐路径。
- 详细变量清单与生产安全 checklist 见 [deployment-config.md](./deployment-config.md) 与 [manual-deployment-runbook.md](./manual-deployment-runbook.md)。

---

## 4. 前端常见修改路径

### 4.1 新增一个页面

1. 在 `frontend/src/views/` 下新建 `XxxView.vue`，使用 `<script setup>`。
2. 在 `frontend/src/router/index.js` 注册路由，需要登录的页面加守卫 meta。
3. 如需调用后端，在 `frontend/src/api/` 下新建或扩展模块，复用 `api/index.js` 的 axios 实例。

### 4.2 新增一个 API 调用

1. 在 `frontend/src/api/` 下找到对应模块（如 `auth.js`、`translate.js`、`checkin.js`），或在 `api/` 下新建文件。
2. 导入 `request from './index'`，封装方法返回 `request.get/post(...)`。
3. 组件中 import 该方法调用，**不要**在组件里直接 `axios`。

### 4.3 用户设置 UI

- 优先放 `frontend/src/views/ProfileView.vue`。
- 膨胀后拆分为 `ProfileView.vue` 下的子组件或独立 `profile/` 目录，**不要**散落到业务页面。

---

## 5. 新增 API 的步骤

1. 明确接口路径（统一前缀 `/api`，Controller 上 `@RequestMapping("/xxx")`）。
2. 明确鉴权要求（默认登录）。
3. 按"后端常见修改路径 → 新增业务接口"流程实现。
4. 若接口会触发 AI provider 调用：
   - 确认是否在内置 Key 模式的受保护接口列表（见 [security-and-quota-plan.md](./security-and-quota-plan.md)）。
   - 确认是否扣费（cost）。
   - 确认输入长度/文件大小限制。
5. 前端在 `api/` 加封装方法。
6. 跑验证命令。

---

## 6. 新增数据库表/字段的步骤

详细规则见 [database-change-guide.md](./database-change-guide.md)，简要流程：

1. **改 `init.sql`**：新增 `CREATE TABLE` 或 `ALTER TABLE`（新字段直接改 `CREATE TABLE` 定义即可）。
2. **写 migration note**：若已有部署需要增量升级，在 `init.sql` 注释或 PR 描述中给出 `ALTER TABLE` 语句。
3. **新建 Entity / Mapper**。
4. **不要**把密钥明文存普通字段。

---

## 7. 如何处理敏感配置

- **数据库密码 / JWT secret / AI API Key**：放 `application-local.yml`（已 `.gitignore`）或环境变量，**不要**提交到仓库。
- **用户自填 API Key**（未来功能）：必须加密存储（`*_api_key_encrypted`），前端只展示 masked key（`*_api_key_masked`，如 `sk-****abcd`），**绝不**返回明文。
- **日志脱敏**：打印 HTTP 请求时去掉 `Authorization` 头与 body 中的 key；provider 异常信息脱敏后再返回前端。
- **示例配置**：README 或文档中只能用 `your_xxx_key` 这类占位符。

---

## 8. 如何写 PR 描述

PR 描述建议包含以下结构：

```markdown
## 背景
（为什么做这个改动，关联 issue）

## 改动内容
- 改了什么（按文件/模块列）
- 没改什么（明确边界，避免 reviewer 误判）

## 数据库变更
- [ ] 无数据库改动
- [ ] 有改动：已更新 init.sql，migration note 如下：
  ```sql
  -- ALTER TABLE ...
  ```

## 验证
- 后端：`cd backend && mvn test` ✅
- 前端：`cd frontend && npm run build` ✅

## 安全自检
- [ ] 未提交任何真实密钥
- [ ] 新增 AI 接口已鉴权
- [ ] API Key 未返回前端
- [ ] 日志未输出 Key
```

---

## 9. 常见错误

| 错误 | 说明 | 正确做法 |
|---|---|---|
| Controller 直接写复杂逻辑 | Prompt 拼接、业务计算、HTTP 调用塞进 Controller | 移到 Service / AI client 层 |
| 把 API Key 返回给前端 | "调试方便"返回明文 key | 永远只返回 masked key |
| 忘记更新 `init.sql` | 新建了表/字段但只改了 Entity | 同步改 `init.sql` 并写 migration note |
| 忘记给接口加鉴权 | 新接口默认 `permitAll` 或没在 SecurityConfig 配置 | 确认接口需登录，用 `@AuthenticationPrincipal` |
| 大面积格式化无关文件 | 顺手格式化整个文件导致 diff 膨胀 | 只格式化改动行，保持 diff 聚焦 |
| 信任前端传入的 `userId` | 直接用 `@RequestParam Long userId` | 用 `@AuthenticationPrincipal AuthUser` 取当前用户 |
| 在组件里直接 `axios` | 绕过统一实例，丢失 JWT 注入与错误处理 | 在 `api/` 下封装，复用 `api/index.js` |
| 改依赖版本/lock 文件 | 为了"修构建"升级依赖 | 不动依赖，找真正原因 |
| 把密钥写进 README/前端代码 | 会被打包/泄露 | 只用占位符，真实密钥走 local profile |
| 混入无关功能 | 一次 PR 改多个不相关的事 | 拆成多个 PR，小步提交 |
