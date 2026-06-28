# IELTS Studio — 雅思备考平台

> **IELTS Studio** 是一款专为雅思考生打造的**全流程机考模拟平台**。支持将 PDF、Word 或图片格式的试卷通过 AI 一键转化为可交互的网页机考界面，涵盖阅读、写作、完形填空等多种题型，并提供 AI 写作批改、智能翻译、AI 助手问答、单词管理等功能，助你高效备考。
>
> **🌐 在线体验：** [http://www.lainpeter.top](http://www.lainpeter.top)
>
> **⭐ 如果觉得有用，请给个 Star 支持一下！**
> *(网站还在持续更新中，更多功能敬请期待下一次更新)*

![Vue3](https://img.shields.io/badge/前端-Vue3+Vite-42b883)
![SpringBoot](https://img.shields.io/badge/后端-SpringBoot3-6db33f)
![MySQL](https://img.shields.io/badge/数据库-MySQL8-blue)
![AI](https://img.shields.io/badge/AI-DeepSeek+Qwen-orange)
![License](https://img.shields.io/badge/License-MIT-yellow)
![Dark Mode](https://img.shields.io/badge/主题-亮色%2F暗色-blueviolet)

---

## 网页预览

### 模拟机考界面（左文右题）
![阅读模式](docs/images/preview-reading1.png)
![阅读模式](docs/images/preview-reading2.png)

### AI 写作批改（智能反馈）
![写作评分](docs/images/preview-writing.png)

### 单词管理（AI 智能释义）
![单词管理](docs/images/preview-words.png)

### 自选完形填空
![完形填空](docs/images/preview-blanks.png)

---

## 目录

- [创作目的](#创作目的)
- [功能特性](#功能特性)
- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [快速开始](#快速开始)
- [环境变量说明](#环境变量说明)
- [试卷解析模式](#试卷解析模式)
- [核心接口](#核心接口)
- [常见问题](#常见问题)
- [开发指引](#开发指引)
- [贡献与反馈](#贡献与反馈)

---

## 创作目的

作者在备考雅思时，发现市面上缺少一款能将 PDF/图片试卷快速转化为网页机考界面的工具。为了更好地适应**雅思电脑考试（机考）**环境，特别创建了这个平台。

希望 IELTS Studio 能帮助更多雅思考生模拟真实的机考体验，提高备考效率。

---

## 功能特性

| 功能模块 | 说明 |
|---|---|
| 用户注册 / 登录 | JWT 无状态认证，支持用户名或邮箱登录 |
| 试卷上传 | 支持 PDF、DOC、DOCX、图片（JPG/PNG），后台异步解析 |
| 普通解析 | 浏览器端 pdf.js 提取文字，扫描版自动 OCR 回退 |
| 精准解析 | 接入 Qwen 视觉模型，适合多栏/扫描/图片版试卷 |
| 模拟考试 | 左文右题分栏布局、独立滚动、倒计时、答题卡 |
| 试卷集 | 多套试卷组合为考试集，连续作答，统一计时 |
| 结果回顾 | 正误统计、错题解析、原文定位高亮 |
| 写作 AI 评分 | DeepSeek 多维度评分（TR / CC / LR / GRA）+ Band 分 |
| 完形填空 | 自选文章段落，AI 自动挖空生成练习 |
| AI 助手 | 考试中随时提问，基于试卷上下文智能回答，支持 Markdown 渲染 |
| 划词翻译 | 翻译模式下选中原文，AI 即时翻译并给出语法注释 |
| 写作引导 | 写作题自动生成思路框架、段落骨架、高分表达建议 |
| 错题本 | 自动收录答错题目，支持标记已掌握 |
| 词书管理 | 创建词书、添加/导入单词、AI 自动查询释义、单词卡片复习 |
| 学习打卡 | 每日学习记录与统计 |
| 深色模式 | 全站暗色主题适配，护眼阅读 |

---

## 技术栈

| 层级 | 技术 |
|---|---|
| **前端** | Vue 3 + Vite + Pinia + Vue Router + Element Plus |
| **样式** | CSS Variables 主题系统 + 暗色模式 |
| **图表** | ECharts（写作数据可视化） |
| **后端** | Spring Boot 3 + MyBatis-Plus + Spring Security |
| **认证** | JWT 无状态认证 |
| **数据库** | MySQL 8.0 |
| **AI 解析** | DeepSeek API（文本解析 + 写作评分 + AI 助手 + 翻译） |
| **视觉解析** | 通义千问 Qwen VL（精准模式 PDF 识别） |
| **文件处理** | pdf.js（前端）+ Apache PDFBox / POI（后端） |

---

## 项目结构

```
ielts-studio/
├── frontend/                              # Vue 3 + Vite 前端
│   ├── src/
│   │   ├── api/                           # API 请求层
│   │   │   ├── index.js                   # Axios 实例（拦截器、Token 注入）
│   │   │   ├── auth.js                    # 认证 API
│   │   │   └── translate.js               # AI 翻译 API
│   │   ├── stores/                        # Pinia 状态管理
│   │   │   ├── auth.js                    # 登录态管理
│   │   │   ├── exam.js                    # 试卷/答题状态
│   │   │   ├── word.js                    # 词书状态
│   │   │   └── theme.js                   # 主题切换（亮色/暗色）
│   │   ├── views/
│   │   │   ├── HomeView.vue               # 首页
│   │   │   ├── LoginView.vue              # 登录
│   │   │   ├── RegisterView.vue           # 注册
│   │   │   ├── ExamsView.vue              # 试卷列表 + 上传
│   │   │   ├── ExamView.vue               # 单卷考试（左文右题 + AI 助手）
│   │   │   ├── CollectionExamView.vue     # 试卷集考试（多套连续作答）
│   │   │   ├── ClozeView.vue              # 完形填空练习
│   │   │   ├── ResultView.vue             # 结果回顾
│   │   │   ├── WordsView.vue              # 词书管理
│   │   │   └── ProfileView.vue            # 个人中心
│   │   ├── components/
│   │   │   ├── NavBar.vue                 # 顶部导航栏（含 GitHub Star 链接）
│   │   │   └── WordCard.vue               # 单词卡片组件
│   │   ├── assets/main.css                # 全局样式 + CSS 变量 + 暗色模式
│   │   ├── router/index.js                # 路由配置（含守卫）
│   │   └── main.js                        # 应用入口
│   └── vite.config.js
│
└── backend/                               # Spring Boot 3 后端
    └── src/main/java/com/ieltsstudio/
        ├── controller/
        │   ├── AuthController.java        # 认证接口
        │   ├── ExamController.java        # 试卷 + AI 助手 + 翻译
        │   ├── ExamCollectionController.java # 试卷集管理
        │   ├── StudyCheckinController.java # 学习打卡
        │   ├── UserController.java        # 用户管理
        │   └── WordBookController.java    # 词书管理
        ├── service/
        │   ├── AiParseService.java        # DeepSeek（解析/评分/助手/翻译）
        │   ├── QwenAiParseService.java    # Qwen / MiMO 视觉解析（精准模式）
        │   ├── AsyncParseService.java     # 异步解析调度
        │   ├── ExamService.java           # 试卷核心逻辑
        │   ├── ExamCollectionService.java # 试卷集逻辑
        │   ├── ClozeService.java          # 完形填空生成
        │   ├── FileParseService.java      # PDF/Word 文字提取
        │   ├── AuthService.java           # 认证逻辑
        │   ├── WordBookService.java       # 词书管理
        │   ├── AsyncWordService.java      # 单词批量导入
        │   └── StudyCheckinService.java   # 打卡统计
        ├── config/                        # 配置类
        ├── security/                      # JWT 认证
        ├── entity/                        # 数据库实体
        ├── mapper/                        # MyBatis-Plus Mapper
        ├── dto/                           # 请求/响应 DTO
        └── common/Result.java             # 统一响应格式
```

---

## 快速开始

### 1. 环境准备

| 依赖 | 版本要求 | 下载地址 |
|---|---|---|
| JDK | 21 | https://adoptium.net |
| Maven | 3.9+ | https://maven.apache.org |
| Node.js | 20 LTS（最低 18+） | https://nodejs.org |
| MySQL | 8.0+ | https://dev.mysql.com/downloads |

> Redis 和 MinIO 为可选依赖，不配置时相关功能会降级，不影响核心流程。

### 2. 克隆项目

```bash
git clone https://github.com/Pandlagon/IELTS-Studio.git
cd IELTS-Studio
```

### 3. 初始化数据库

```bash
mysql -u root -p < backend/src/main/resources/db/init.sql
```

脚本会自动创建 `ielts_studio` 数据库和全部数据表。

### 4. 配置后端

复制 `.env.example` 为 `.env` 并填入实际值（`.env` 已在 `.gitignore` 中，不会被提交）：

```bash
cp .env.example .env
```

关键变量示例（完整清单见 `.env.example` 与 [docs/deployment-config.md](./docs/deployment-config.md)）：

```env
DB_URL=jdbc:mysql://localhost:3306/ielts_studio?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
DB_USERNAME=root
DB_PASSWORD=your_local_password
JWT_SECRET=change-me-please-use-64-random-chars-or-more
AI_KEY_ENCRYPTION_SECRET=change-me-please-use-32-random-chars-or-more
DEEPSEEK_API_KEY=your_deepseek_key          # BUILTIN 模式必填
AI_PRECISE_PROVIDER=mimo                     # 精准解析提供方：mimo 或 qwen
MIMO_API_KEY=your_mimo_key                   # AI_PRECISE_PROVIDER=mimo 时必填
QWEN_API_KEY=your_qwen_key                   # AI_PRECISE_PROVIDER=qwen 时必填
```

后端 `application.yml` 已通过 `spring.config.import: optional:file:../.env[.properties]` 自动加载根目录 `.env`，从 `backend/` 目录运行 `mvn spring-boot:run` 即可，**无需修改 `application.yml`**。

> ⚠️ 生产环境必须通过系统环境变量注入敏感配置，不要依赖 `.env` 文件或 `application.yml` 默认值。详见 [docs/deployment-config.md](./docs/deployment-config.md)。

**获取 API Key：**
- **DeepSeek**：[platform.deepseek.com](https://platform.deepseek.com)
- **通义千问 Qwen / DashScope**：[dashscope.aliyuncs.com](https://dashscope.aliyuncs.com)

### 5. （可选）启用 Redis 缓存
- 在 `.env` 中设置 `APP_REDIS_ENABLED=true`，并按需填写 `SPRING_DATA_REDIS_HOST/PORT/PASSWORD/DATABASE`。
- 不配置或设为 `false` 时使用单机内存限流，应用不会连接 Redis，单实例本地开发无需启动 Redis。
- 多实例部署必须设为 `true`，否则各实例独立计数，rate limit 不共享。
- Redis 不可用时自动 fallback 到内存限流，不影响主流程。
- 详细行为见 [docs/deployment-config.md](./docs/deployment-config.md) 第 5 节。

### 6. 启动后端

```bash
cd backend
mvn spring-boot:run
```

接口地址：`http://localhost:8080/api`

### 7. 启动前端

```bash
cd frontend
npm install
npm run dev
```

访问：`http://localhost:3000`

> 如需修改后端地址，创建 `frontend/.env.local` 并设置 `VITE_API_BASE_URL=http://localhost:8080/api`

---

## 环境变量说明

IELTS Studio 通过环境变量管理生产配置，避免在 `application.yml` 中维护明文密钥。Authoritative references：

- [.env.example](./.env.example) — 环境变量模板，复制为 `.env` 后填入实际值（`.env` 已在 `.gitignore` 中）
- [docs/deployment-config.md](./docs/deployment-config.md) — 完整部署配置说明（必填 / 可选变量、Redis 开关、加密密钥、生产安全 checklist）
- [docs/manual-deployment-runbook.md](./docs/manual-deployment-runbook.md) — 普通环境部署与本地调试 Runbook
- [docs/release-checklist.md](./docs/release-checklist.md) — 发布检查项、部署方式、数据库迁移、回滚说明

常用变量：

- `DB_URL` / `DB_USERNAME` / `DB_PASSWORD`
- `JWT_SECRET`
- `AI_KEY_ENCRYPTION_SECRET`
- `INIT_ADMIN_USERNAME` / `INIT_ADMIN_EMAIL` / `INIT_ADMIN_PASSWORD`
- `CORS_ALLOWED_ORIGINS`
- `APP_REDIS_ENABLED`（默认 `false`，多实例部署需设为 `true`）
- `DEEPSEEK_API_KEY` / `MIMO_API_KEY` / `QWEN_API_KEY`
- `VITE_API_BASE_URL`

> ⚠️ 不要提交真实 API Key / 数据库密码 / JWT secret / 加密密钥到 Git；生产环境必须通过系统环境变量注入。

---

## Deployment configuration

生产部署所需的环境变量、安全注意事项与本地开发示例见：

- **[docs/deployment-config.md](./docs/deployment-config.md)**：完整部署配置说明（必填 / 可选变量、Redis 开关、加密密钥、生产安全 checklist）
- **[.env.example](./.env.example)**：环境变量模板，复制为 `.env` 后填入实际值（`.env` 已在 `.gitignore` 中）

> ⚠️ 生产环境必须通过环境变量注入 `JWT_SECRET`、`DB_PASSWORD`、`AI_KEY_ENCRYPTION_SECRET`、`CORS_ALLOWED_ORIGINS` 等敏感配置，禁止依赖 `application.yml` 中的开发默认值。

---

## CI and release checklist

GitHub Actions 会在每次 push / pull request 时自动运行 backend tests、frontend build 与基础安全 grep 检查（见 [.github/workflows/ci.yml](./.github/workflows/ci.yml)）。

当前阶段 CI 只负责验证与产出临时构建 artifact，**不执行自动部署**——服务器部署由维护者手动执行。详见：

- [docs/deployment-config.md](./docs/deployment-config.md)：部署环境变量与生产安全 checklist
- [docs/release-checklist.md](./docs/release-checklist.md)：发布检查项、部署方式、数据库迁移、回滚说明
- [docs/ai-provider-smoke-test.md](./docs/ai-provider-smoke-test.md)：发布后手动 smoke test checklist

---

## Deployment and local development

For non-Docker local development and manual deployment, see:

- [docs/manual-deployment-runbook.md](./docs/manual-deployment-runbook.md)：普通环境部署与本地调试 Runbook（本地启动 / 生产构建 / CORS / Smoke test / 常见问题排查）
- [docs/deployment-config.md](./docs/deployment-config.md)：完整部署配置说明（必填 / 可选变量、Redis 开关、加密密钥、生产安全 checklist）
- [docs/release-checklist.md](./docs/release-checklist.md)：发布检查项、部署方式、数据库迁移、回滚说明

---

## 试卷解析模式

### 普通解析（推荐优先尝试）

适合文字可选中的普通 PDF、Word 文档。

```
浏览器 pdf.js 提取文字 → OCR 回退 → DeepSeek AI 结构化解析
```

✅ 速度快、费用低 · ⚠️ 复杂排版稳定性稍弱

### 精准解析（复杂试卷推荐）

适合双栏/扫描版/图片版试卷。

```
PDF 逐页渲染为图片 → MiMO / Qwen 视觉模型识别 → AI 结构化解析 → 合并
```

> 默认走 MiMO（`AI_PRECISE_PROVIDER=mimo`），可在 `.env` / 环境变量中切换为 Qwen；用户也可在前端 Profile 页面切换为自填 Key（USER 模式）。

✅ 解析质量高 · ⚠️ 耗时较长，建议解析后人工复核

### 上传建议

- 按题型分开上传（阅读和写作分别上传）
- 包含表格/流程图的试卷优先使用精准解析
- Word 文件在普通解析模式下比 PDF 更稳定

---

## 核心接口

### 认证

| 路径 | 方法 | 说明 |
|---|---|---|
| `/api/auth/register` | POST | 注册 |
| `/api/auth/login` | POST | 登录（返回 JWT） |
| `/api/auth/profile` | GET | 获取用户信息 |

### 试卷

| 路径 | 方法 | 说明 |
|---|---|---|
| `/api/exams` | GET | 试卷列表 |
| `/api/exams/upload` | POST | 上传试卷 |
| `/api/exams/{id}` | GET / DELETE | 试卷详情 / 删除 |
| `/api/exams/{id}/questions` | GET | 题目列表 |
| `/api/exams/submit` | POST | 提交答案 |
| `/api/exams/history` | GET | 考试历史 |
| `/api/exams/records/{id}` | GET | 记录详情 |
| `/api/exams/grade-writing` | POST | AI 写作评分 |
| `/api/exams/ai-chat` | POST | AI 助手问答 |
| `/api/exams/translate` | POST | AI 翻译 |
| `/api/exams/errors` | GET | 错题本 |

### 试卷集

| 路径 | 方法 | 说明 |
|---|---|---|
| `/api/exam-collections` | GET / POST | 列表 / 创建 |
| `/api/exam-collections/{id}` | GET / DELETE | 详情 / 删除 |

### 词书

| 路径 | 方法 | 说明 |
|---|---|---|
| `/api/words/books` | GET / POST | 列表 / 创建 |
| `/api/words/books/{id}/entries` | GET | 词条列表 |
| `/api/words/books/default/quick-add` | POST | 快速批量添加 |

### 学习打卡

| 路径 | 方法 | 说明 |
|---|---|---|
| `/api/study-checkin/today` | POST | 今日打卡 |
| `/api/study-checkin/stats` | GET | 打卡统计 |

---

## 常见问题

**前端访问不到后端？**
- 确认后端已启动（默认 `8080` 端口）
- 检查 `.env.local` 中 `VITE_API_BASE_URL` 是否正确
- 浏览器 DevTools → Network 查看状态码（401 未登录 / 404 路径错 / 500 后端异常）

**数据库连接失败？**
- 检查用户名密码、确认已执行 `init.sql`、检查 MySQL 时区设置

**试卷解析后题目为空？**
- BUILTIN 模式：确认 `DEEPSEEK_API_KEY` 已注入（或在前端 Profile 页面切换为 USER 模式自填 Key）
- 后端日志搜索 `[AsyncParseService]`
- 尝试切换精准解析模式

**精准解析无结果？**
- 检查 `AI_PRECISE_PROVIDER` 设置（`mimo` 或 `qwen`）
- 选 `mimo` 时确认 `MIMO_API_KEY` 已注入；选 `qwen` 时确认 `QWEN_API_KEY` 已注入
- 确认网络可访问对应 provider 域名

**写作评分失败？**
- 确认 DeepSeek API Key 有效
- 写作内容建议 50 词以上

---

## 开发指引

### 关键入口文件

| 方向 | 文件 |
|---|---|
| AI Prompt 调整 | `backend/service/AiParseService.java` |
| 试卷解析流程 | `backend/service/AsyncParseService.java` |
| 考试页 UI | `frontend/views/ExamView.vue` |
| 试卷集 UI | `frontend/views/CollectionExamView.vue` |
| 完形填空 UI | `frontend/views/ClozeView.vue` |
| 结果回顾 UI | `frontend/views/ResultView.vue` |
| 全局样式/暗色模式 | `frontend/assets/main.css` |

### 常用命令

```bash
# 前端
npm run dev          # 开发服务器
npm run build        # 生产构建

# 后端
mvn spring-boot:run       # 启动
mvn clean package         # 打包为 jar
```

### Agent 开发说明

如果使用 Claude Code、GLM、Codex 或其他 AI coding agent 参与开发，请先阅读：

- [AGENTS.md](./AGENTS.md)
- [docs/agent-development.md](./docs/agent-development.md)
- [docs/ai-provider-architecture.md](./docs/ai-provider-architecture.md)
- [docs/security-and-quota-plan.md](./docs/security-and-quota-plan.md)
- [docs/database-change-guide.md](./docs/database-change-guide.md)

---

## 贡献与反馈

项目持续开发中，欢迎参与贡献！

- **🐛 Bug 反馈** → 提交 [Issue](https://github.com/Pandlagon/IELTS-Studio/issues)
- **💡 功能建议** → 提交 [Issue](https://github.com/Pandlagon/IELTS-Studio/issues)
- **🔧 代码贡献** → 提交 [Pull Request](https://github.com/Pandlagon/IELTS-Studio/pulls)
- **⭐ 觉得有用** → 给个 [Star](https://github.com/Pandlagon/IELTS-Studio) 支持一下！

---

本项目仅供学习与个人开发使用，请勿用于商业目的。
# IELTS-Studio

