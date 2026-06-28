# Manual Deployment & Local Development Runbook

> 本文件面向"第一次接手 IELTS Studio 的人"，说明在 **非 Docker 环境** 下如何本地开发、调试、构建、生产启动与排查问题。
>
> 配套阅读：
> - [deployment-config.md](./deployment-config.md)：完整环境变量清单与生产安全 checklist
> - [release-checklist.md](./release-checklist.md)：发布检查项、构建 artifact、数据库迁移、回滚
> - [ai-provider-smoke-test.md](./ai-provider-smoke-test.md)：发布后 AI Provider smoke test
> - [admin-management.md](./admin-management.md)：管理端能力与安全规则
>
> 本 runbook 只写文档，不引入 Docker / docker-compose / 自动部署 / systemd 真落地 / Nginx 真落地。
> 涉及 systemd / Nginx 处仅给示例代码块，**不作为仓库配置文件落地**。

---

## 1. Overview

IELTS Studio 是一个雅思备考平台，前端 Vue 3 + Vite，后端 Spring Boot 3 + MyBatis-Plus + Spring Security + JWT，数据库 MySQL 8。AI 能力通过 DeepSeek（文本）/ MiMO / Qwen（视觉）等 OpenAI-compatible provider 提供，支持「站点内置 Key（BUILTIN）」与「用户自填 Key（USER）」两种模式。

本 runbook 覆盖：

- 本地开发环境搭建（MySQL / 可选 Redis / 可选 MinIO）
- 后端本地启动（Maven 与 IDE 两种方式）
- 前端本地启动
- 生产构建与生产启动
- 前端静态托管
- CORS / 反向代理注意事项
- 管理员账号 bootstrap
- Smoke test checklist
- 常见问题排查

不要求使用 Docker，不绑定特定操作系统。Windows / macOS / Linux 均可，命令示例会分别给出 bash 与 PowerShell 两种风格。

---

## 2. Required runtime

### Backend

```txt
Java 21          （pom.xml 中 java.version=21，必须用 JDK 21）
Maven 3.9+       （用于构建与 mvn spring-boot:run）
Spring Boot 3.2.3（由 spring-boot-starter-parent 锁定，无需手动指定）
MySQL 8.x        （utf8mb4 / utf8mb4_unicode_ci）
Redis optional   （APP_REDIS_ENABLED=false 时走单机内存限流，不连接 Redis）
MinIO optional   （当前版本文件以内存字节处理，MinIO 为预留扩展；上传相关功能如启用对象存储则需要）
```

### Frontend

```txt
Node.js 20 LTS recommended（Vite 5 最低要求 Node 18+，推荐 LTS 20）
npm              （随 Node 一起安装）
Vite 5.2         （开发服务器与生产构建）
```

> 不强制某一操作系统。Windows 用户建议用 PowerShell 执行 env 示例；macOS / Linux 用户用 bash。JDK 21 可从 [Adoptium](https://adoptium.net) 安装，Node 20 LTS 可从 [nodejs.org](https://nodejs.org) 安装。

---

## 3. Repository layout

```txt
backend/       Spring Boot 3 后端（Java 21，包根 com.ieltsstudio）
frontend/      Vue 3 + Vite 前端
docs/          项目文档（deployment / release / smoke test / admin / database / ai-provider 等）
.github/       GitHub Actions CI（ci.yml：mvn test + npm run build + security grep）
.env.example   根目录环境变量模板（复制为 .env 后填值，.env 已在 .gitignore 中）
README.md      项目入口文档
AGENTS.md      AI coding agent 协作规范
CLAUDE.md      Claude 专用入口
```

后端关键路径：

- 主类：`backend/src/main/java/com/ieltsstudio/IeltsStudioApplication.java`（`@SpringBootApplication`）
- 主配置：`backend/src/main/resources/application.yml`
- AI 配置：`backend/src/main/resources/application-ai.yml`
- 数据库初始化：`backend/src/main/resources/db/init.sql`
- 接口前缀：`/api`（由 `server.servlet.context-path: /api` 统一添加）

---

## 4. Local development setup

### 4.1 克隆仓库

```bash
git clone <repo-url>
cd IELTS-Studio
```

> 不要在文档里写死私有 URL。请使用你实际拥有的仓库地址（GitHub 上的公开/私有镜像均可）。

### 4.2 环境变量模板

仓库根目录提供 `.env.example`，复制为 `.env` 后填入本地实际值：

```bash
cp .env.example .env
```

重要约定：

- **Java 后端不会自动读取根目录 `.env`，除非通过 `spring.config.import` 加载。** 本项目 `application.yml` 已配置 `spring.config.import: optional:file:../.env[.properties]`，从 `backend/` 子目录运行 `mvn spring-boot:run` 时会自动加载根目录 `.env`，**无需指定 profile**。
- `.env.example` 只是模板，不是自动加载机制本身；`.env` 是被 `spring.config.import` 显式加载的。
- 本地开发也可以用 IDE Run Configuration 配 env，或用 `application-local.yml`（已 `.gitignore`）+ `-Dspring-boot.run.profiles=local`。
- 生产环境必须由系统环境变量 / 启动脚本 / process manager 注入，**不要**依赖 `.env` 文件。

必须遵守：

```txt
不要提交 .env
不要提交真实 key / password / secret
```

`.env` 已在 `.gitignore` 中，不会被提交；如果意外新增了含密钥的文件，请先 `git rm --cached` 再处理。

### 4.3 MySQL 初始化

创建数据库：

```sql
CREATE DATABASE IF NOT EXISTS ielts_studio
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

执行初始化脚本（脚本会以 `CREATE TABLE IF NOT EXISTS` 创建全部表，重复执行不会破坏数据）：

```bash
mysql -u root -p ielts_studio < backend/src/main/resources/db/init.sql
```

`init.sql` 包含全部业务表与 AI Provider 主线相关表（`user_ai_settings` / `ai_usage_quota` / `ai_usage_records` / `admin_user_permissions` / `admin_operation_logs`）。详细表结构见 [database-change-guide.md](./database-change-guide.md)。

---

## 5. Local backend startup

提供两种方式，任选其一。

### 方式 A：Maven 直接启动

最简方式（依赖根目录 `.env` 提供敏感值，无需 profile）：

```bash
cd backend
mvn spring-boot:run
```

如果不用 `.env`，也可以直接在 shell 中导出环境变量。

Linux / macOS 示例：

```bash
export DB_URL='jdbc:mysql://localhost:3306/ielts_studio?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true'
export DB_USERNAME='root'
export DB_PASSWORD='your-local-password'
export JWT_SECRET='local-dev-jwt-secret-change-me'
export AI_KEY_ENCRYPTION_SECRET='local-dev-ai-secret-change-me'

cd backend
mvn spring-boot:run
```

Windows PowerShell 示例：

```powershell
$env:DB_URL="jdbc:mysql://localhost:3306/ielts_studio?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="your-local-password"
$env:JWT_SECRET="local-dev-jwt-secret-change-me"
$env:AI_KEY_ENCRYPTION_SECRET="local-dev-ai-secret-change-me"

cd backend
mvn spring-boot:run
```

> 本地开发若要加载 `application-local.yml`（已 `.gitignore`），改用 `mvn spring-boot:run -Dspring-boot.run.profiles=local`。

启动成功标志：

- 控制台无异常栈。
- 日志可见 `Started IeltsStudioApplication in X.XXX seconds`。
- 接口地址：`http://localhost:8080/api`。

### 方式 B：IDE 启动

1. 用 IntelliJ IDEA（ Ultimate 或 Community + 手动配 Maven）或 VS Code（装 "Extension Pack for Java"）打开 `backend/` 目录。
2. 在 `backend/src/main/java/com/ieltsstudio/IeltsStudioApplication.java` 找到 `@SpringBootApplication` 主类（**不要假设主类名**，请在代码中确认 `@SpringBootApplication` 标注的类）。
3. 右键 → Run。首次运行会创建一个 Run Configuration。
4. 在 Run Configuration 的 Environment 变量里填入 `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` / `JWT_SECRET` / `AI_KEY_ENCRYPTION_SECRET` 等（与方式 A 一致）。
5. 重新运行，确认日志无异常。

> IDE 启动不会自动加载根目录 `.env`（除非 IDE 配了 env file 插件），建议直接在 Run Configuration 里配环境变量，或使用 `application-local.yml` + `local` profile。

---

## 6. Local frontend startup

```bash
cd frontend
npm install
npm run dev
```

说明：

- 默认 Vite 端口是 **3000**（`package.json` 中 `dev: vite --port 3000`）。
- `VITE_API_BASE_URL` 应指向后端 `/api`。
- 本地默认可用（`frontend/.env.example` 内容）：

```env
VITE_API_BASE_URL=http://localhost:8080/api
```

如需覆盖默认值，复制为 `.env.local`：

```bash
cd frontend
cp .env.example .env.local
# 编辑 .env.local 填入实际值
```

> 项目已有 `frontend/.env.example`，**不要**新增多套 env 模板。`.env.local` 不会被提交（已在 `.gitignore`）。

访问：`http://localhost:3000`。

---

## 7. MySQL setup

### 7.1 安装

- Windows：从 [dev.mysql.com](https://dev.mysql.com/downloads) 下载 MySQL 8 Installer。
- macOS：`brew install mysql`。
- Linux（Debian/Ubuntu）：`sudo apt install mysql-server`。

### 7.2 字符集与时区

数据库必须使用 `utf8mb4` / `utf8mb4_unicode_ci`（已在 §4.3 的建库语句中指定）。JDBC URL 中建议带 `serverTimezone=Asia/Shanghai`（或你所在时区），避免时区不一致导致时间字段错乱。

### 7.3 初始化

见 §4.3。执行 `init.sql` 后，所有表以 `CREATE TABLE IF NOT EXISTS` 创建，重复执行安全。

### 7.4 账号建议

- 本地开发可用 `root` + 本地密码。
- **生产必须创建专用账号**（非 root）+ 强密码，通过 `DB_USERNAME` / `DB_PASSWORD` 注入。

---

## 8. Redis setup

Redis 是**可选项**。

### 默认（不启用 Redis）

```txt
APP_REDIS_ENABLED=false
```

此时 USER 模式 rate limit 使用单机内存 fallback，应用**不会**连接 Redis。单实例本地开发无需启动 Redis。

### 启用 Redis

```bash
APP_REDIS_ENABLED=true
SPRING_DATA_REDIS_HOST=127.0.0.1
SPRING_DATA_REDIS_PORT=6379
SPRING_DATA_REDIS_PASSWORD=
SPRING_DATA_REDIS_DATABASE=0
```

行为说明：

- 单机开发可以不启 Redis（`APP_REDIS_ENABLED=false`）。
- **多实例部署建议启 Redis**（`APP_REDIS_ENABLED=true`），否则各实例独立计数，rate limit 不共享，用户可绕过限制。
- Redis 只用于分布式 rate limit / cache 等，**不存 API Key 明文**。
- Redis 不可用时自动 fallback 到内存限流，不向用户暴露 Redis 异常。
- Redis key 形如 `ai:rate:user:{userId}:feature:{feature}:minute:{epochMinute}`，不含敏感信息。

详细行为见 [deployment-config.md §5](./deployment-config.md)。

---

## 9. MinIO setup

MinIO 是**可选项**。当前版本文件以字节数组方式在内存中处理，MinIO 为预留扩展项；如启用对象存储，文件上传相关功能需要 MinIO。

配置变量：

```txt
MINIO_ENDPOINT
MINIO_ACCESS_KEY
MINIO_SECRET_KEY
MINIO_BUCKET_NAME
```

默认值（仅用于本地，**生产必须替换**）：

```env
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=change-me
MINIO_BUCKET_NAME=ielts-studio
```

说明：

- 如果上传功能依赖 MinIO，生产必须配置。
- 本地可以使用本地 MinIO 或已有对象存储。
- **不要提交 MinIO secret**。
- 当前项目实际上没有强依赖 MinIO 启动（不配 MinIO 也能启动后端），文档定位为"文件上传相关功能需要"。

---

## 10. AI provider configuration

完整变量清单见 [deployment-config.md §3](./deployment-config.md)。简要说明：

```txt
BUILTIN 模式需要后端配置 DEEPSEEK / MIMO / QWEN 等 provider key
USER 模式由用户在前端配置自己的 key
AI_KEY_ENCRYPTION_SECRET 用于加密用户自填 key
修改 AI_KEY_ENCRYPTION_SECRET 会导致旧 encrypted key 无法解密
```

### 10.1 BUILTIN 模式（站点内置 Key）

后端通过环境变量注入：

```env
DEEPSEEK_API_KEY=...                 # 文本解析 / 写作评分 / AI 助手 / 翻译 / 完形填空
DEEPSEEK_BASE_URL=https://api.deepseek.com
DEEPSEEK_MODEL=deepseek-chat

AI_PRECISE_PROVIDER=mimo             # 精准解析提供方：mimo 或 qwen

MIMO_API_KEY=...                     # AI_PRECISE_PROVIDER=mimo 时必填
MIMO_BASE_URL=https://api.xiaomimimo.com/v1
MIMO_MODEL=mimo-v2.5

QWEN_API_KEY=...                     # AI_PRECISE_PROVIDER=qwen 时必填
QWEN_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
QWEN_MODEL=qwen3.6-plus
```

BUILTIN 模式下用户消耗站点 credits（`ai_usage_quota` 表）。

### 10.2 USER 模式（用户自填 Key）

用户在前端 Profile 页面配置自己的 provider / baseUrl / model / apiKey。用户自填 key 经 `AI_KEY_ENCRYPTION_SECRET` AES/GCM 加密后存入 `user_ai_settings` 表，前端只展示 masked key（如 `sk-****abcd`），**不返回 raw key / encrypted key**。

USER 模式不消耗站点 credits，但仍有 rate limit。

### 10.3 加密密钥

- `AI_KEY_ENCRYPTION_SECRET` 必须注入 32 位以上随机字符串，不能依赖开发 fallback。
- **更换 `AI_KEY_ENCRYPTION_SECRET` 后，旧 encrypted key 将无法解密**，用户需重新填写 API Key。
- 缺失时后端回退到开发态密钥并打印 WARN 日志，**禁止用于生产**。

必须强调：

```txt
不要在 frontend env 放任何 AI Provider key
不要把真实 API Key 写入仓库
```

---

## 11. Production build

### Backend

```bash
cd backend
mvn clean package -DskipTests
```

产物：

```txt
backend/target/*.jar
```

> 生产构建如需运行测试，去掉 `-DskipTests`。正式发布前 `mvn test` 必须通过。

### Frontend

仓库存在 `frontend/package-lock.json`，生产构建建议 `npm ci`（确定性安装，更快更安全）：

```bash
cd frontend
npm ci
npm run build
```

产物：

```txt
frontend/dist
```

> 如果仓库不存在 `package-lock.json`，则改用 `npm install`。本项目当前**存在** `package-lock.json`，建议 `npm ci`。

---

## 12. Production startup

### Backend

普通启动示例：

```bash
java -jar backend/target/ielts-studio-backend-*.jar
```

带环境变量示例（Linux / macOS）：

```bash
SERVER_PORT=8080 \
SPRING_PROFILES_ACTIVE=prod \
DB_URL='jdbc:mysql://127.0.0.1:3306/ielts_studio?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true' \
DB_USERNAME='ielts_studio' \
DB_PASSWORD='change-me' \
JWT_SECRET='change-me-use-long-random-secret' \
AI_KEY_ENCRYPTION_SECRET='change-me-use-long-random-secret' \
CORS_ALLOWED_ORIGINS='https://your-frontend-domain.com' \
java -jar backend/target/ielts-studio-backend-*.jar
```

Windows PowerShell 示例：

```powershell
$env:SERVER_PORT="8080"
$env:SPRING_PROFILES_ACTIVE="prod"
$env:DB_URL="jdbc:mysql://127.0.0.1:3306/ielts_studio?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
$env:DB_USERNAME="ielts_studio"
$env:DB_PASSWORD="change-me"
$env:JWT_SECRET="change-me-use-long-random-secret"
$env:AI_KEY_ENCRYPTION_SECRET="change-me-use-long-random-secret"
$env:CORS_ALLOWED_ORIGINS="https://your-frontend-domain.com"

java -jar backend/target/ielts-studio-backend-*.jar
```

提醒：

```txt
示例值必须替换
生产不要使用 change-me
JWT_SECRET 必须足够长且随机（64 位以上）
AI_KEY_ENCRYPTION_SECRET 必须稳定保存，不能随意更换（更换后旧 encrypted key 无法解密）
CORS_ALLOWED_ORIGINS 必须设为实际前端域名，禁止保留 localhost
```

### Frontend

见 §13。前端 `frontend/dist` 由静态文件服务托管，不随后端 jar 一起部署。

---

## 13. Frontend static hosting

`frontend/dist` 应由静态文件服务托管，可选：

```txt
Nginx
Caddy
Apache
Node static server
其他平台静态托管（Vercel / Netlify / Cloudflare Pages 等）
```

不强制某一种。选择建议：

- **Nginx / Caddy / Apache**：自建服务器常用，可同时承担反向代理（见 §14）。
- **Node static server**：轻量场景，如 `npx serve frontend/dist`。
- **平台静态托管**：无服务器场景，但需保证前端域名能访问后端 API（注意 CORS）。

部署要点：

- 将 `frontend/dist` 整个目录上传到静态服务器根目录。
- 前端是 SPA，所有未命中静态文件的路径应回退到 `index.html`（Nginx 用 `try_files`，Caddy 用 `try_files` 或 `rewrite`）。
- `VITE_API_BASE_URL` 在 build 时注入；同源部署可留空走 `/api`，跨域部署填后端完整地址。

---

## 14. CORS and reverse proxy notes

### 14.1 CORS

- 后端 API 默认路径是 `/api`（由 `server.servlet.context-path: /api`）。
- 前端 `VITE_API_BASE_URL` 应与后端地址对应（同源走 `/api`，跨域走完整 URL）。
- **生产前端域名必须加入 `CORS_ALLOWED_ORIGINS`**（逗号分隔，如 `https://example.com,https://www.example.com`）。
- 留空时仅允许 `localhost` / `127.0.0.1` / `[::1]`（仅适用于本地开发）。
- **不要在生产用 `*` 放开 CORS**。

### 14.2 反向代理

如果用反向代理，可以把 `/api` 转发到后端，前端同源部署，避免跨域。Nginx 示例（**仅示例，未作为仓库配置文件落地**）：

```nginx
# ──────────────────────────────────────────────────────────────
# 仅示例，未作为仓库配置文件落地。生产请按实际域名/证书调整。
# ──────────────────────────────────────────────────────────────
server {
    listen 443 ssl http2;
    server_name example.com;

    # ssl_certificate     /etc/ssl/example.com.crt;
    # ssl_certificate_key /etc/ssl/example.com.key;

    # 前端静态资源
    root /var/www/ielts-studio-frontend;
    index index.html;

    # SPA history fallback
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 反向代理 /api 到后端
    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 120s;   # AI 调用可能较慢
        client_max_body_size 60m;  # 与后端 multipart 限制匹配
    }
}
```

> 该 Nginx 配置仅为文档示例，**不是**仓库中的配置文件，也不作为 Phase 9A 的落地物。生产部署时由维护者按实际环境编写。

---

## 15. Admin bootstrap and verification

### 15.1 如何确认管理员账号

管理员账号以 `users.role = 'ADMIN'` 标识。Spring Security `hasRole("ADMIN")` 在路由层拦截 `/admin/**`，Controller 内 `requireAdmin(AuthUser)` 做防御性二次校验。

### 15.2 init.sql 不含默认 admin

`backend/src/main/resources/db/init.sql` **不**预置任何默认 admin 用户。请勿编造默认密码。首次部署需要通过以下方式之一创建管理员。

### 15.3 方式 A：环境变量初始化（推荐，幂等）

后端 `AdminBootstrapConfig` 在启动时通过 `ApplicationRunner` 执行幂等初始化，读取 `INIT_ADMIN_USERNAME` / `INIT_ADMIN_EMAIL` / `INIT_ADMIN_PASSWORD`：

- **三项任意为空 → 跳过**。
- **库中已存在任意 ADMIN（含已禁用 `deleted=1`）→ 跳过**，避免重复创建、避免初始密码被重新激活。
- **目标 username/email 未被占用 → 创建新 ADMIN 用户**。
- **目标 username/email 已被占用（且库中无 ADMIN）→ 将该现有 USER 升级为 ADMIN 并重置密码**。
- 密码使用 `PasswordEncoder.encode(...)` 存储 BCrypt 哈希，**日志只记录 username，不记录明文密码**。

注入示例（生产用环境变量，本地写进 `.env`）：

```env
INIT_ADMIN_USERNAME=admin
INIT_ADMIN_EMAIL=admin@example.com
INIT_ADMIN_PASSWORD=your-strong-initial-password
```

启动日志出现 `管理员账号初始化完成：username=...` 或 `已将现有用户升级为管理员并重置密码：username=...` 即成功。

**生产推荐流程：**

1. 首次部署时注入 `INIT_ADMIN_*` 三项环境变量。
2. 启动后端，日志确认初始化成功。
3. 用该账号登录后台，**立即在「用户管理」页面重置密码**为高强度密码。
4. 之后可清除 `INIT_ADMIN_PASSWORD` 环境变量（保留也不影响，因幂等跳过）。

详细规则见 [deployment-config.md §2.1](./deployment-config.md)。

### 15.4 方式 B：注册 + 现有管理员提升

如果库中已有 ADMIN，直接由现有 ADMIN 在后台「用户管理」提升其他用户角色即可，无需再用环境变量。

### 15.5 安全约定

```txt
不要在生产保留默认弱密码
如果手动改数据库密码，必须使用 BCrypt hash，不要明文写入 users.password
推荐通过已有注册 / 后台创建用户流程创建管理员
```

> `users.password` 字段必须存 BCrypt 哈希。直接 SQL 改密码而不哈希会导致无法登录。

### 15.6 验证

- 用 admin 账号登录， NavBar 用户下拉可见「用户管理 / 额度管理 / AI 使用统计 / 审计日志 / 权限」入口（按权限显示）。
- 普通用户登录不显示上述入口；直接访问 `/admin/**` 路由会被前端守卫拦截，后端返回 403。

---

## 16. Smoke test checklist

部署后最小 smoke checklist。

### Backend

```txt
启动无异常
连接 MySQL 成功
Redis disabled/enabled 路径按预期
JWT 登录正常
/admin/** 普通 USER 不能访问
```

### Frontend

```txt
npm run build 成功
登录/注册页面可打开
个人中心可打开
Admin 菜单按权限显示
```

### AI

```txt
USER 模式自填 key 不消耗 BUILTIN credits
BUILTIN 模式 credits 正常扣减
Provider failure 不泄露 key
When checking usage records, note that one exam upload can generate both a main parse record and post-processing records such as `HEADING_EXTRACT`.
```

### Admin

```txt
用户管理可访问
额度管理可访问
权限管理可配置
审计日志有记录
审计日志 summary 不含 password/key/token
```

更详细的 AI Provider smoke test 见 [ai-provider-smoke-test.md](./ai-provider-smoke-test.md)。

---

## 17. Common troubleshooting

### 17.1 MySQL 连接失败

排查方向：

- 确认 MySQL 服务已启动（`mysqladmin ping` 或 `systemctl status mysql`）。
- 确认 `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` 正确。
- 确认 `ielts_studio` 数据库已创建并执行了 `init.sql`。
- 确认 JDBC URL 中 `serverTimezone` 与 MySQL 时区一致。
- 确认防火墙未拦截 3306 端口。
- 后端日志搜索 `HikariPool` / `Communications link failure`。

### 17.2 `Public Key Retrieval is not allowed`

MySQL 8 + JDBC 8 默认不允许公钥检索。JDBC URL 必须带 `allowPublicKeyRetrieval=true`（项目默认 URL 已带）：

```txt
jdbc:mysql://localhost:3306/ielts_studio?...&useSSL=false&allowPublicKeyRetrieval=true
```

如果生产要启用 SSL，改 `useSSL=true` 并配置证书，同时移除 `allowPublicKeyRetrieval=true`。

### 17.3 CORS 报错

浏览器控制台出现 `blocked by CORS policy`：

- 确认 `CORS_ALLOWED_ORIGINS` 已设为前端实际域名（含协议，逗号分隔）。
- 确认没有在生产用 `*` 放开（会被浏览器连同 cookie/credentials 一起拒绝）。
- 同源部署（前端与 `/api` 同域）可避免 CORS，走反向代理（见 §14.2）。

### 17.4 前端请求 404 / `/api/api` 重复

典型错误：前端 `VITE_API_BASE_URL` 与后端 `context-path` 重复拼接。

- 后端 `context-path` 已是 `/api`，前端请求路径不要再加 `/api` 前缀。
- 正确：`VITE_API_BASE_URL=http://localhost:8080/api`，业务 API 调用 `/auth/login` → 实际请求 `http://localhost:8080/api/auth/login`。
- 错误：前端又拼了一层 `/api`，导致 `http://localhost:8080/api/api/auth/login` → 404。

### 17.5 Redis 连接失败

- `APP_REDIS_ENABLED=false` 时不应连接 Redis；如仍报错，检查是否误开了 enabled。
- `APP_REDIS_ENABLED=true` 时确认 host/port/password 正确，Redis 服务已启动。
- 应用**不会**因 Redis 不可用而崩溃：自动 fallback 到内存限流，AI 功能仍可用。

### 17.6 JWT secret 太短或重启后 token 失效

- `JWT_SECRET` 生产必须 64 位以上随机字符串；过短会被拒绝或弱化安全性。
- **更换 `JWT_SECRET` 后，所有已签发的 JWT 立即失效**，用户需重新登录。这是预期行为。
- 重启后 token 失效通常是 `JWT_SECRET` 被重新生成导致，确保生产 `JWT_SECRET` 稳定不变。

### 17.7 AI key 解密失败

- `AI_KEY_ENCRYPTION_SECRET` 与加密时使用的密钥不一致 → 旧 encrypted key 无法解密。
- 排查：是否在部署中途更换过 `AI_KEY_ENCRYPTION_SECRET`？
- 修复：让用户在 Profile 页面重新填写 API Key（用当前 secret 重新加密）。
- **不要**为了"修复"而把 secret 改回旧值，这会导致新 key 又无法解密。

### 17.8 前端 build 失败

- 确认 Node 版本 ≥ 18（推荐 20 LTS）：`node -v`。
- 删除 `frontend/node_modules` 与 `frontend/package-lock.json` 后重新 `npm install`（最后手段，会改 lockfile，谨慎）。
- 检查 `VITE_API_BASE_URL` 是否合法 URL。
- 查看完整报错，常见是依赖版本冲突或语法错误；不要为"修构建"随意改 `package.json` 版本。

### 17.9 后端端口被占用

`Port 8080 was already in use`：

- 找到占用进程并结束，或改用其他端口：`SERVER_PORT=8081`（同时改前端 `VITE_API_BASE_URL`）。
- Windows：`netstat -ano | findstr :8080` → `taskkill /PID <pid> /F`。
- Linux/macOS：`lsof -i :8080` → `kill -9 <pid>`。

### 17.10 Admin 菜单不显示

- 确认登录账号 `users.role = 'ADMIN'`。
- 显式权限模式下（`admin_user_permissions` 表非空），需确认该 ADMIN 拥有对应 `*_VIEW` 权限。兼容模式（表空）下所有 ADMIN 自动拥有全部权限。
- 前端 NavBar 通过 `GET /api/admin/permissions/me` 懒加载权限，确认该接口返回 200 且包含所需权限。
- 清浏览器缓存 / 重新登录刷新 JWT 与权限缓存。

---

## 18. What this runbook does not cover

本 runbook **不**覆盖以下内容（留到后续阶段或不在本仓库范围）：

```txt
Docker / docker-compose 部署
自动部署（CI/CD 直推服务器）
GitHub Release 正式发包
systemd service 文件真落地到仓库
Nginx 配置文件真落地到仓库（本文 Nginx 块仅示例）
真实生产服务器连接与运维
数据库高可用 / 读写分离
Redis 集群 / 哨兵部署
MinIO 集群部署
AI Provider 调用链修改 / quota 扣费逻辑修改 / rate limit 修改 / Admin 权限逻辑修改
Phase 9B 及之后阶段
```

涉及上述内容时，请参考：

- [release-checklist.md](./release-checklist.md)：当前部署模式与未来 Release 规划
- [deployment-config.md](./deployment-config.md)：环境变量与生产安全 checklist
- [ai-provider-architecture.md](./ai-provider-architecture.md)：AI Provider 主线设计与阶段规划
- [admin-management.md](./admin-management.md)：Admin 能力范围与安全规则
- [database-change-guide.md](./database-change-guide.md)：数据库改动流程
