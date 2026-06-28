# Release Checklist

> 本文件说明 IELTS Studio 发布前的检查项与部署流程。
> 配套阅读：[deployment-config.md](./deployment-config.md)、[ai-provider-smoke-test.md](./ai-provider-smoke-test.md)。

---

## 1. CI checks

每次 push / PR 触发 GitHub Actions（见 `.github/workflows/ci.yml`），以下 job 必须全部通过才能发布：

- **Backend tests**：`mvn -B test`（Java 21，Maven 缓存）
- **Frontend build**：`npm ci && npm run build`（Node 20，npm 缓存）
- **Security grep**：检查旧 direct provider 调用残留、生产代码硬编码 provider key、前端 API Key 本地存储、AI 相关前端代码 `console.log`

> CI 不执行自动部署，只产出临时构建 artifact。

---

## 2. Build artifacts

CI 会上传以下临时 artifact（保存期 7 天）：

| Artifact | 内容 | 用途 |
|---|---|---|
| `ielts-studio-backend-jar` | `backend/target/*.jar` | 可直接 `java -jar` 运行 |
| `ielts-studio-frontend-dist` | `frontend/dist` | 交给 Nginx / 静态文件服务 |

> Artifacts 是 CI 临时构建产物，**不是正式 GitHub Release**。维护者可从 Actions 运行页下载后手动部署。

---

## 3. Deployment ownership

```txt
当前 CI 不执行自动部署。
服务器部署由维护者手动执行。
```

CI 只负责验证与产出 artifact；把代码部署到生产服务器是维护者 / 服务器管理员的职责。

---

## 4. Short-term deployment: git pull

推荐的短期部署方式（单机 / 小规模）：

### Backend

```bash
# 登录服务器，进入项目目录
cd /opt/ielts-studio
git pull

cd backend
mvn clean package -DskipTests
# 通过环境变量注入生产配置后启动
java -jar target/ielts-studio-backend-*.jar
```

### Frontend

```bash
cd /opt/ielts-studio/frontend
npm ci
npm run build
# 将 frontend/dist 交给 Nginx 或静态文件服务
```

---

## 5. Alternative: CI artifacts

如服务器无 Maven / Node 构建环境，可下载 CI artifact 部署：

1. 在 GitHub Actions 运行页下载 `ielts-studio-backend-jar` 与 `ielts-studio-frontend-dist`。
2. 上传到服务器。
3. `java -jar` 启动后端；`dist` 交给 Nginx。

注意：

- artifact 不是正式 release 包。
- artifact 有 7 天保存期限，过期需重新触发 CI。
- 下载 artifact 需要仓库写权限或 public 仓库的读权限。

---

## 6. Database migration

### 首次部署

执行初始化脚本：

```bash
mysql -u root -p < backend/src/main/resources/db/init.sql
```

### 升级已有部署

- **升级前必须备份数据库**：`mysqldump -u root -p ielts_studio > backup_$(date +%Y%m%d).sql`
- 核对 `user_ai_settings` / `ai_usage_quota` / `ai_usage_records` 三表结构是否与最新 `init.sql` 一致。
- 增量变更参考 `docs/database-change-guide.md` 中的 migration note，执行对应 `ALTER TABLE`。

---

## 7. Environment variables

生产环境必须通过环境变量注入以下敏感配置（禁止依赖 `application.yml` 开发默认值）：

- `DB_URL` / `DB_USERNAME` / `DB_PASSWORD`
- `JWT_SECRET`（64 位以上随机字符串）
- `AI_KEY_ENCRYPTION_SECRET`（32 位以上随机字符串；更换后旧 encrypted key 无法解密）
- `CORS_ALLOWED_ORIGINS`（实际前端域名）
- BUILTIN 模式所需 provider key：`DEEPSEEK_API_KEY` / `MIMO_API_KEY` / `QWEN_API_KEY`

完整变量清单见 [deployment-config.md](./deployment-config.md) 与 `.env.example`。

---

## 8. Redis mode

| 场景 | `APP_REDIS_ENABLED` | 行为 |
|---|---|---|
| 单实例 | `false`（默认） | 内存限流，不连 Redis |
| 多实例 | `true` | Redis 分布式限流，计数共享；Redis 不可用时 fallback 内存 |

> 多实例部署必须设为 `true`，否则各实例独立计数，rate limit 不共享。

---

## 9. Smoke test

发布后必须执行 [ai-provider-smoke-test.md](./ai-provider-smoke-test.md) 中的 checklist，至少覆盖：

1. 用户 AI settings（BUILTIN / USER 模式切换，masked key 展示）
2. 用户 AI usage（quota 加载、recent records）
3. BUILTIN quota 不足时返回清晰错误
4. USER rate limit 触发
5. 管理端统计（ADMIN 可见，USER 403）
6. 安全检查（devtools response 无 raw key，日志无 Authorization/Bearer/sk-）

---

## 10. Rollback notes

- 回滚代码：`git checkout <previous-tag>` 后重新构建部署。
- 回滚数据库：用步骤 6 的备份恢复 `mysql -u root -p ielts_studio < backup_YYYYMMDD.sql`。
- 回滚后确认 `AI_KEY_ENCRYPTION_SECRET` 与加密 key 兼容（不要在回滚中途更换 secret，否则旧 encrypted key 无法解密）。
- 如回滚涉及 `ai_usage_records.provider` 字段缺失等结构差异，优先用备份恢复而非部分回滚。

---

## 正式 GitHub Release

```txt
当前阶段不创建正式 GitHub Release。
Tag-based Release / Docker image / automated deployment 留到后续阶段（Phase 7D+）。
```
