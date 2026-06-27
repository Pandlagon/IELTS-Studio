# AGENTS.md — AI Coding Agent 协作规范

> 本文件是 IELTS Studio 仓库面向所有 AI coding agent（Claude Code、GLM、Codex、Cursor 等）的主说明文档。
> 任何 agent 在本仓库进行代码改动前，都应先完整阅读本文件，并遵守其中的约定。
> 更详细的开发指引见 [docs/agent-development.md](./docs/agent-development.md)。

---

## 1. 项目简介

**IELTS Studio** 是一个雅思备考平台，支持将 PDF / Word / 图片格式的试卷通过 AI 一键转化为可交互的网页机考界面，涵盖阅读、写作、完形填空等题型，并提供 AI 写作批改、智能翻译、AI 助手问答、单词管理、学习打卡等功能。

技术栈：

- **前端**：Vue 3 + Vite + Pinia + Vue Router + Element Plus
- **后端**：Spring Boot 3 + MyBatis-Plus + Spring Security + JWT
- **数据库**：MySQL 8
- **AI**：
  - DeepSeek：文本解析、写作评分、AI 助手、翻译、完形填空等文本任务
  - Qwen / MiMO：PDF / 图片 / 多模态精准解析任务
- 后端统一接口前缀为 `/api`

---

## 2. 仓库结构

```
ielts-studio/
├── frontend/                              # Vue 3 + Vite 前端
│   └── src/
│       ├── api/                           # API 请求层（axios 实例 + 各业务 API）
│       ├── stores/                        # Pinia 状态管理
│       ├── views/                         # 页面组件
│       ├── components/                    # 通用组件
│       ├── router/                        # 路由配置（含守卫）
│       ├── utils/                         # 工具函数
│       └── assets/                        # 样式与静态资源
│
├── backend/                               # Spring Boot 3 后端
│   └── src/main/
│       ├── java/com/ieltsstudio/
│       │   ├── controller/                # 接口层
│       │   ├── service/                   # 业务逻辑层 + AI 调用
│       │   ├── entity/                    # 数据库实体
│       │   ├── mapper/                    # MyBatis-Plus Mapper
│       │   ├── dto/                       # 请求/响应 DTO
│       │   ├── common/                    # Result 统一响应
│       │   ├── config/                    # 配置类
│       │   ├── security/                  # JWT 认证（AuthUser 等）
│       │   ├── infra/                     # 基础设施（Redis 等）
│       │   └── util/                      # 工具类
│       └── resources/
│           ├── application.yml            # 主配置
│           ├── application-ai.yml         # AI 相关配置
│           └── db/init.sql                # 数据库初始化脚本
│
├── docs/                                  # 项目文档
│   ├── agent-development.md               # Agent 开发指引
│   ├── ai-provider-architecture.md        # AI Provider 改造设计
│   ├── security-and-quota-plan.md         # 安全与额度设计
│   └── database-change-guide.md           # 数据库改动指南
│
├── AGENTS.md                              # 本文件
├── CLAUDE.md                              # Claude 专用入口
└── README.md
```

> 数据库初始化文件位于 `backend/src/main/resources/db/init.sql`，新增表/字段必须同步更新该文件。

---

## 3. 后端开发约定

- **框架**：Spring Boot 3，包根 `com.ieltsstudio`。
- **严格分层**：`Controller` / `Service` / `Entity` / `Mapper` / `DTO`，各层职责清晰，不要越层。
- **统一返回**：所有接口返回值使用 `com.ieltsstudio.common.Result<T>` 包装，提供 `success()` / `error()` / `notFound()` / `unauthorized()` / `forbidden()` 等工厂方法。不要在 Controller 里直接返回裸对象。
- **登录用户**：通过 `@AuthenticationPrincipal AuthUser authUser` 注入当前登录用户，从 `authUser.getId()` 获取用户 ID，**不要**自己从请求头解析 JWT，也**不要**信任前端传入的 `userId`。
- **Controller 只做编排**：参数校验、调用 Service、组装 Result。**不要在 Controller 里堆复杂业务逻辑**，更不要在 Controller 里直接拼 Prompt 或调用外部 HTTP。
- **AI 调用集中化**：所有 AI provider 调用必须集中在 Service / AI client 层（如 `AiParseService`、`QwenAiParseService` 及后续抽象的 `OpenAiCompatibleClient`），**不要散落在 Controller**。Controller 只负责把请求交给 Service。
- **DTO 校验**：使用 `jakarta.validation`（`@Valid`、`@NotBlank` 等）做参数校验，校验失败由全局异常处理器统一处理。
- **异常处理**：业务异常请抛出或通过 `GlobalExceptionHandler` 统一捕获，**不要**在 Controller 用 try-catch 吞掉异常再返回 `Result.error`。

---

## 4. 前端开发约定

- **框架**：Vue 3 **Composition API**（`<script setup>`），不要混用 Options API。
- **UI 库**：Element Plus，组件按需引入已在入口统一配置。
- **API 请求集中**：所有后端请求集中在 `frontend/src/api/`，**不要**在组件里直接 `axios.get(...)`。新增接口时在 `api/` 下对应文件添加方法，组件再调用。
- **axios 实例**：`frontend/src/api/index.js` 导出的实例会自动从 `localStorage` 读取 JWT 并附加到 `Authorization` 头，响应拦截器统一处理 401/403/5xx。请始终使用该实例，**不要**新建 axios 实例。
- **状态管理**：跨页面/跨组件的状态放 Pinia store（`frontend/src/stores/`），组件局部状态用 `ref`/`reactive`。
- **用户设置 UI**：用户设置相关界面优先放在 `frontend/src/views/ProfileView.vue`，后续若膨胀再拆分为 `profile/settings` 子组件，**不要**把设置散落到各个业务页面。
- **样式**：全局样式与 CSS 变量在 `frontend/src/assets/main.css`，支持亮色/暗色主题，新增样式优先复用已有 CSS 变量。

---

## 5. 数据库改动约定

- 修改数据库前，**优先查看** `backend/src/main/resources/db/init.sql`，了解现有表结构。
- **新增表或字段必须同步更新 `init.sql`**，保证新部署能直接初始化出最新结构。
- 若涉及已有部署的增量变更，必须在文档（PR 描述或 `docs/database-change-guide.md`）中写 **migration note**，给出对应的 `ALTER TABLE` 语句，参考 `init.sql` 中已有的注释式 migration 写法。
- **不要**把密钥、API Key、JWT secret 明文存在普通字段里；敏感凭据必须加密存储（详见 `docs/database-change-guide.md` 中 `user_ai_settings` 设计）。
- 表名使用蛇形命名（snake_case），字段名使用蛇形命名，时间字段统一 `created_at` / `updated_at`。
- 详细的数据库改动流程见 [docs/database-change-guide.md](./docs/database-change-guide.md)。

---

## 6. AI 改造方向

> 本阶段（Phase 1）只写设计文档，**不实现** AI Provider 业务逻辑，**不重构**现有 Java/Vue 代码。

后续计划把 AI 调用改造成用户可配置的双 Provider 架构：

- **Text Provider**：文本任务，例如写作评分、AI Chat、翻译、普通试卷解析、完形填空生成/批改
- **Vision Provider**：多模态任务，例如 PDF / 图片精准解析、扫描版试卷解析、写作 Task 1 图表识别

用户可以选择：

1. **使用站点内置 API Key**，并消耗站点内置额度（credits）
2. **使用自己的 API Key**，并在用户中心分别配置 Text Provider 和 Vision Provider

自填 API Key 模式支持：

- 预设 Provider，例如 DeepSeek、Qwen、MiMO
- 自定义 OpenAI-compatible Base URL / Model / API Key

**核心规则：**

- Provider 尽量走 OpenAI-compatible `/chat/completions` 协议，统一客户端抽象。
- **API Key 不能明文返回给前端**，前端只展示 masked key（如 `sk-****abcd`）。
- **日志不能输出 API Key**，打印请求时必须脱敏。
- 自定义 Base URL 要做基本校验（协议、host），防止 SSRF。
- AI 接口必须鉴权，内置 Key 模式要有用户级 quota，自填 Key 模式也要有基础 rate limit。

详细设计见 [docs/ai-provider-architecture.md](./docs/ai-provider-architecture.md) 与 [docs/security-and-quota-plan.md](./docs/security-and-quota-plan.md)。

---

## 7. 安全要求

- **鉴权**：所有 AI 相关接口必须登录鉴权，匿名调用一律拒绝。
- **内置 Key 模式**：必须有用户级 quota（credits），超额拒绝。详见 `docs/security-and-quota-plan.md`。
- **自填 Key 模式**：不消耗站点 credits，但必须有基础 rate limit、timeout、输入长度限制。
- **输入限制**：AI 输入要有限制（文本长度、文件大小、单次请求数量），防止滥用。
- **错误脱敏**：**不要**把 provider 原始错误完整暴露给用户，后端日志保留必要排查信息但脱敏（去掉 key、Authorization 头）。
- **密钥管理**：不要提交真实 API Key、数据库密码、JWT secret 到仓库；密钥配置走 `application-local.yml`（已在 `.gitignore`）或环境变量。

---

## 8. 验证命令

完成改动后，请运行以下命令确认未破坏构建：

```bash
# 后端：编译 + 测试（若无测试，至少保证编译通过）
cd backend
mvn test
# 若测试不存在或环境受限，至少运行：
# mvn compile

# 前端：生产构建
cd frontend
npm run build
```

- 前端依赖已安装（`node_modules` 存在）时直接 `npm run build`；若 `node_modules` 不存在，先 `npm install`。
- 不要为了"修构建"而随意改动依赖版本或 lock 文件。

---

## 9. 禁止事项

- ❌ **不要提交真实 API Key、数据库密码、JWT secret** 到仓库（包括注释、示例代码、截图）。
- ❌ **不要把密钥写进 README 或前端代码**，前端代码会被打包到浏览器，任何密钥都会泄露。
- ❌ **不要大面积格式化无关文件**，保持 diff 聚焦在本次任务范围内。
- ❌ **不要在同一改动里混入无关 UI/业务功能**，一次 PR 只做一件事。
- ❌ **不要改依赖管理方式**（不要切换包管理器、不要改 `pom.xml` 的依赖坐标/版本、不要动 `package-lock.json` / `pom.xml` 的依赖锁定），除非任务明确要求。
- ❌ **不要绕过 `Result` 统一返回**，不要在 Controller 里自己拼 JSON。
- ❌ **不要把 API Key 返回给前端**，即使是"调试用"也不行。
- ❌ **不要忘记给新接口加鉴权**。
- ❌ **不要忘记更新 `init.sql`**。

---

## 10. 文档语言

- 项目文档以**中文**为主，可保留必要的英文技术名词（如 `Controller`、`Provider`、`OpenAI-compatible`、`JWT` 等）。
- 代码注释同样以中文为主，专有名词保留英文。
