# CLAUDE.md

本仓库的主要 Agent 开发规范见 [AGENTS.md](./AGENTS.md)。

在开始任何代码修改前，请先阅读 AGENTS.md，并遵守其中关于项目结构、后端分层、前端约定、AI Provider、数据库变更和安全边界的要求。

简要提醒：

- **先读现有代码再改**：不要凭猜测修改，先理解上下文（Controller / Service / Entity / Mapper / DTO 分层、`Result` 统一返回、`@AuthenticationPrincipal AuthUser` 注入登录用户）。
- **小步提交**：一次改动只做一件事，不混入无关 UI/业务功能，不大面积格式化无关文件。
- **遵守 AI Provider 约定**：AI 调用集中在 Service / AI client 层，不散落到 Controller；API Key 不明文返回前端，日志不输出 Key；详细设计见 `docs/ai-provider-architecture.md`。
- **遵守安全约定**：AI 接口必须鉴权，内置 Key 模式有用户级 quota，自填 Key 模式有基础 rate limit；详见 `docs/security-and-quota-plan.md`。
- **遵守数据库约定**：改表先看 `backend/src/main/resources/db/init.sql`，新表/字段必须同步更新 `init.sql` 并写 migration note；详见 `docs/database-change-guide.md`。
- **不要输出或提交任何密钥**：包括真实 API Key、数据库密码、JWT secret。
- **验证命令**：后端 `cd backend && mvn test`，前端 `cd frontend && npm run build`。
