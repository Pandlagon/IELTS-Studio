# PR Description Draft

> 本文件是开 PR 时可直接复制使用的描述模板。内容应如实反映已落地范围，不夸大、不隐瞒未完成项。

---

## Summary

This PR upgrades IELTS Studio with a multi-provider AI architecture, user-provided API key support, quota / rate-limit management, admin management, CI, audit logs, and deployment documentation. The PR also includes final PR-readiness polish: README legacy config cleanup, deployment doc consistency verification, a final security grep, and full `mvn test` + `npm run build` validation.

## Major changes

### AI Provider architecture

- Added unified AI provider abstraction (`AiTaskType` / `AiKeyMode` / `AiProviderPreset` / `AiCredentials` / `AiProviderRegistry` / `AiSettingsService`).
- Added BUILTIN / USER key modes (site key with credits vs. user-supplied key with rate limit).
- Added Text / Vision provider separation (DeepSeek for text; MiMO / Qwen for vision).
- Added OpenAI-compatible provider client (`OpenAiCompatibleClient`) covering `/chat/completions` for both text and `image_url` multimodal messages.
- Removed legacy `callDeepSeek` / `QwenDocumentParseService` / `SHARED_HTTP_CLIENT` direct HTTP code (CI grep enforces no revival).

### User AI settings

- Added `user_ai_settings` table with `*_api_key_encrypted` (AES/GCM) + `*_api_key_masked` columns.
- Added `UserAiSettingsService` + `UserAiSettingsController` + DTOs (`@ToString.Exclude` on `apiKey`, no plaintext return).
- Added frontend AI settings card (`AiSettingsCard.vue`) integrated into `ProfileView.vue`.
- API keys are encrypted server-side; only masked keys (e.g. `sk-****abcd`) are returned to the frontend.
- API key input is never refilled; raw key is not written to `localStorage` / `sessionStorage` / Pinia.

### Quota and usage

- Added weekly BUILTIN credits (`ai_usage_quota`, default 30/week) with atomic pre-debit + failure rollback.
- Added `ai_usage_records` (SUCCESS / FAILED / REJECTED) with `provider` field.
- Added USER-mode rate limit (per user per feature per minute, in-memory sliding window).
- Added optional Redis distributed rate limit (`APP_REDIS_ENABLED=true`), with automatic fallback to in-memory when Redis is unavailable.
- Added user-facing usage view (`GET /api/users/me/ai-usage`) and admin aggregate statistics (`GET /api/admin/ai-usage/summary`, `/recent`).

### Admin console

- Added user management (`/admin/users`): list / search / role update / disable / enable / reset password / create.
- Added quota management (`/admin/quotas`): set total / grant / reset used, with `(user_id, period_start)` unique constraint and defensive reload.
- Added lightweight admin permissions (`admin_user_permissions` table + 8-value `AdminPermission` enum), with compatibility mode (table empty → all admins have all permissions) and 5 safety rules (cannot remove last `ADMIN_PERMISSIONS_MANAGE`, etc.).
- Added admin operation audit logs (`admin_operation_logs` table + `AdminAuditLogService` + `AdminAuditLogController`), recording 9 high-risk write actions with sanitized summaries (no password / API Key / token / Authorization / Bearer / sk-* values).

### Security

- No plaintext API keys returned to frontend; no encrypted keys returned either.
- API Key encryption via `AI_KEY_ENCRYPTION_SECRET` (AES/GCM/NoPadding). Secret rotation invalidates existing encrypted keys (documented).
- Custom Base URL validation (protocol + host) to mitigate SSRF.
- Provider error sanitization (`AiLogSanitizer` strips `Authorization: Bearer` / `Bearer xxx` / `sk-xxx`, truncates to 500 chars).
- Audit log summary sanitization (`AdminAuditLogService.sanitizeSummary` covers `password` / `apiKey` / `token` / `Authorization` / `Bearer` / `sk-*`, truncates to 1000 chars).
- All AI endpoints require authentication; all `/admin/**` endpoints require `ADMIN` role + fine-grained permission second-check.
- `userId` is always derived from `@AuthenticationPrincipal AuthUser`; frontend-supplied `userId` is not trusted.

### Deployment and CI

- Added `.env.example` (root) and `frontend/.env.example` as templates; `.env` is `.gitignore`d.
- Added `docs/deployment-config.md` (9 sections: required/optional vars, AI built-in keys, encryption secret notes, Redis rate limit, DB init, frontend vars, production security checklist, local development examples).
- Added `docs/manual-deployment-runbook.md` (18 sections: runtime, repo layout, local setup, MySQL/Redis/MinIO, AI provider config, production build/startup, frontend static hosting, CORS/reverse proxy, admin bootstrap, smoke test, troubleshooting).
- Added `docs/release-checklist.md` (CI checks, build artifacts, deployment ownership, git-pull deployment, CI artifact deployment, DB migration, env vars, Redis mode, smoke test, rollback notes).
- Added `docs/ai-provider-smoke-test.md` (8-section manual smoke test checklist).
- Added `.github/workflows/ci.yml` (3 jobs: backend-test, frontend-build, security-grep). CI only validates and produces temporary artifacts; it does **not** auto-deploy.

### Documentation polish (Phase 9B)

- Removed outdated `application.yml` direct-edit instructions from `README.md` (replaced with `.env` workflow).
- Removed outdated env var table (`spring.datasource.*` / `ai.deepseek.api-key` / `qwen.api-key` / `jwt.secret` / `app.redis.enabled`) from `README.md`; the README now points to `.env.example` / `docs/deployment-config.md` / `docs/manual-deployment-runbook.md` / `docs/release-checklist.md` as authoritative references.
- Removed deleted `QwenDocumentParseService.java` / dead `LlamaParseService.java` from the README project structure.
- Updated JDK requirement from 17+ to 21 (matches `pom.xml` `java.version=21` and runbook).
- Updated troubleshooting section to reference new env var names (`DEEPSEEK_API_KEY` / `AI_PRECISE_PROVIDER` / `MIMO_API_KEY` / `QWEN_API_KEY`).
- Updated `docs/agent-development.md` to reflect `.env` workflow as primary local config path (with `application-local.yml` as legacy fallback) and to forbid revival of legacy direct provider code.

## Validation

- [ ] `cd backend && mvn test` passes
- [ ] `cd frontend && npm run build` passes
- [ ] GitHub Actions CI passed (3 jobs: backend-test / frontend-build / security-grep)
- [ ] Security grep verified (no real secrets, no legacy direct provider code, no frontend API key leakage)
- [ ] No real secrets committed (only `change-me` / `sk-test-*` / `sk-fake-*` placeholders in `.env.example` and tests)
- [ ] No Dockerfile / docker-compose / auto-deploy added
- [ ] No GitHub Secrets / GitHub Release added
- [ ] No database schema changes outside documented `init.sql` updates (Phase 8C/8D tables already migrated in prior phases)
- [ ] No business logic / quota / rate limit / admin permission logic changes in this PR-readiness phase

## Notes

- **CI produces temporary build artifacts only; it does not deploy.** Artifacts (`ielts-studio-backend-jar`, `ielts-studio-frontend-dist`) have a 7-day retention and are not formal GitHub Releases.
- **Docker / docker-compose / automated deployment / GitHub Release are intentionally deferred** to a future phase (post-Phase 9B). The current deployment model is manual: `git pull` + `mvn clean package` + `java -jar` on the server.
- **systemd / Nginx configurations are intentionally NOT committed** as repo files. The runbook provides example snippets only; production deployment is the maintainer's responsibility.
- **Email verification and password reset are intentionally deferred** to future work; current registration does not require email verification, and password reset must be performed by an admin via the user management console.
- **`LlamaParseService.java` remains as dead code** (marked TODO for future cleanup). It is not invoked by any production path and is superseded by `OpenAiCompatibleClient`; it will be removed in a future cleanup phase.
- **`AI_KEY_ENCRYPTION_SECRET` rotation invalidates existing encrypted API keys**: users must re-enter their API keys after rotation. This is by design (AES/GCM encryption).
- **Multi-instance deployments must set `APP_REDIS_ENABLED=true`**; otherwise rate limit counters are per-instance and users can bypass limits.
