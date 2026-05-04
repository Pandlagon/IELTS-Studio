# IELTS Studio — 雅思备考平台

> **IELTS Studio** 是一款专为雅思考生打造的**全流程机考模拟平台**。支持将 PDF、Word 或图片格式的试卷通过 AI 一键转化为可交互的网页机考界面，涵盖阅读、写作、完形填空等多种题型，并提供 AI 写作批改、智能翻译、AI 助手问答、单词管理等功能，助你高效备考。
>
> **🌐 在线体验：** [http://www.lainpeter.top:8888](http://www.lainpeter.top:8888)
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
        │   ├── QwenAiParseService.java    # Qwen 视觉解析（精准模式）
        │   ├── QwenDocumentParseService.java # Qwen 文档解析
        │   ├── LlamaParseService.java     # LlamaParse 解析（备选）
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
| JDK | 17+ | https://adoptium.net |
| Maven | 3.9+ | https://maven.apache.org |
| Node.js | 18+ | https://nodejs.org |
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

编辑 `backend/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ielts_studio?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: root              # 你的 MySQL 用户名
    password: your_password     # 你的 MySQL 密码

ai:
  deepseek:
    api-key: your_deepseek_key  # DeepSeek API Key（必填）

qwen:
  api-key: your_dashscope_key  # DashScope API Key（精准解析，可选）
```

**获取 API Key：**
- **DeepSeek**：[platform.deepseek.com](https://platform.deepseek.com)
- **DashScope（Qwen）**：[dashscope.aliyuncs.com](https://dashscope.aliyuncs.com)

### 5. 启动后端

```bash
cd backend
mvn spring-boot:run
```

接口地址：`http://localhost:8080/api`

### 6. 启动前端

```bash
cd frontend
npm install
npm run dev
```

访问：`http://localhost:3000`

> 如需修改后端地址，创建 `frontend/.env.local` 并设置 `VITE_API_BASE_URL=http://localhost:8080/api`

---

## 环境变量说明

### 后端（application.yml）

| 配置项 | 说明 | 必填 |
|---|---|---|
| `spring.datasource.username` | MySQL 用户名 | ✅ |
| `spring.datasource.password` | MySQL 密码 | ✅ |
| `ai.deepseek.api-key` | DeepSeek API Key | ✅ |
| `qwen.api-key` | DashScope API Key | 可选 |
| `jwt.secret` | JWT 签名密钥 | ✅（生产环境必须更换） |
| `spring.data.redis.*` | Redis 连接 | 可选 |

### 前端（.env.local）

| 变量名 | 说明 | 默认值 |
|---|---|---|
| `VITE_API_BASE_URL` | 后端 API 地址 | `/api` |

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
PDF 逐页渲染为图片 → Qwen 视觉模型识别 → AI 结构化解析 → 合并
```

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
- 检查 `ai.deepseek.api-key` 配置
- 后端日志搜索 `[AsyncParseService]`
- 尝试切换精准解析模式

**精准解析无结果？**
- 检查 `qwen.api-key` 配置
- 确认网络可访问 `dashscope.aliyuncs.com`

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

---

## 贡献与反馈

项目持续开发中，欢迎参与贡献！

- **🐛 Bug 反馈** → 提交 [Issue](https://github.com/Pandlagon/IELTS-Studio/issues)
- **💡 功能建议** → 提交 [Issue](https://github.com/Pandlagon/IELTS-Studio/issues)
- **🔧 代码贡献** → 提交 [Pull Request](https://github.com/Pandlagon/IELTS-Studio/pulls)
- **⭐ 觉得有用** → 给个 [Star](https://github.com/Pandlagon/IELTS-Studio) 支持一下！

---

## License

本项目采用 MIT 许可证，仅供学习与个人使用。
