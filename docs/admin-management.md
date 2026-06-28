# Admin Management

> 本文件说明 IELTS Studio 管理端用户管理的能力范围与安全规则。
> 配套阅读：[ai-provider-architecture.md](./ai-provider-architecture.md)、[security-and-quota-plan.md](./security-and-quota-plan.md)。

---

## 1. Current role model

当前系统沿用两级角色模型（不做复杂 RBAC）：

- `USER` — 普通用户
- `ADMIN` — 管理员

角色存储在 `users.role` 字段（`VARCHAR(20)`），由 Spring Security `hasRole("ADMIN")` 在路由层拦截 `/admin/**`，Controller 内 `requireAdmin(AuthUser)` 做防御性二次校验。

---

## 2. User management

Phase 8A 已落地的管理端用户管理能力（路径前缀 `/api/admin/users`）：

- **list / search users** — `GET /api/admin/users?page=1&pageSize=20&keyword=&role=&status=`
  - `keyword` 匹配 username / email
  - `role` 过滤：USER / ADMIN
  - `status` 过滤：ACTIVE（deleted=0）/ DISABLED（deleted=1）/ ALL
  - `page` 最小 1，`pageSize` 范围 1~100（越界自动 clamp）
- **get user detail** — `GET /api/admin/users/{id}`（包含已禁用用户）
- **update role** — `PUT /api/admin/users/{id}/role`（body: `{ "role": "ADMIN" }`）
- **disable user** — `PUT /api/admin/users/{id}/disable`（deleted=1）
- **enable user** — `PUT /api/admin/users/{id}/enable`（deleted=0，不改变 role）
- **reset password** — `POST /api/admin/users/{id}/reset-password`（body: `{ "newPassword": "..." }`）
- **create user** — `POST /api/admin/users`（body: `{ "username","email","password","role" }`，BCrypt 加密，role 白名单 USER/ADMIN）

### 逻辑删除处理

`users.deleted` 字段标注了 MyBatis-Plus `@TableLogic`，普通 wrapper 查询会自动过滤 `deleted=1` 的用户。
管理端需要能查到/操作已禁用用户，因此 `UserMapper` 扩展了自定义 SQL 方法
（`selectAdminUsers` / `countAdminUsers` / `selectUserIncludingDeleted` / `updateDeletedById` 等），
这些方法使用原生 `@Select` / `@Update` 注解，不受 `@TableLogic` 自动过滤影响，
**不破坏**现有普通用户查询语义（`findByUsername` / `findByEmail` / `selectById` 仍只返回启用用户）。

---

## 3. Safety rules

管理端操作必须遵守以下安全规则（后端 `AdminUserService` 强制校验，前端仅做提示）：

- **cannot disable yourself** — 不能禁用自己
- **cannot demote yourself** — 不能降级自己的管理员角色
- **cannot disable the last ADMIN** — 不能禁用最后一个启用的 ADMIN（`countActiveAdmins() <= 1` 时拒绝）
- **cannot demote the last ADMIN** — 不能降级最后一个启用的 ADMIN
- **password reset uses BCrypt** — 重置密码使用 `PasswordEncoder.encode(...)` 存储 BCrypt 哈希
- **password is never returned** — 所有 DTO（`AdminUserDto` / `AdminUserPageDto`）均不包含 `password` 字段
- **no plaintext password in logs** — `AdminResetPasswordRequest.newPassword` 通过 `@ToString.Exclude` 排除，service 只 log userId 不 log 密码
- **role whitelist** — `role` 只允许 `USER` / `ADMIN`，`SUPER_ADMIN` 等非法值在 service 层拒绝
- **userId / role from AuthUser only** — 当前操作者 ID 与角色只从 `@AuthenticationPrincipal AuthUser` 取，不信任前端传入

---

## 4. Future phases

- **Phase 8C**: ✅ 已完成 — Admin 轻量权限增强（见 §6）。保留 USER/ADMIN 两级基础角色，新增 `admin_user_permissions` 表与后台权限分配，支持不同 ADMIN 拥有不同管理权限。

---

## 5. Quota management

Phase 8B 支持当前周期 quota 管理（路径前缀 `/api/admin/quotas`）：

- **list quotas** — `GET /api/admin/quotas?page=1&pageSize=20&keyword=&role=&status=`
  - 筛选同用户管理：`keyword`（username/email）、`role`（USER/ADMIN）、`status`（ACTIVE/DISABLED/ALL）
  - 包含已禁用用户，复用 8A 的 `selectAdminUsers` / `countAdminUsers`
- **get user quota** — `GET /api/admin/quotas/users/{userId}`
- **set total** — `PUT /api/admin/quotas/users/{userId}/total`（body: `{ "creditsTotal": 100 }`）
- **grant credits** — `POST /api/admin/quotas/users/{userId}/grant`（body: `{ "credits": 20 }`）
- **reset used** — `POST /api/admin/quotas/users/{userId}/reset-used`

### 周期与默认值

- 周期计算与 `AiUsageGuard` / `AiUsageQueryService` 一致：周一 00:00:00 到下周一 00:00:00（服务器默认时区）。
- 默认每周 credits = 30（`DEFAULT_WEEKLY_CREDITS` 常量，与 `init.sql` 列默认值一致）。

### 无 quota 行时的处理

- **读操作**（list / get）：返回虚拟默认视图 `30/0/30`，`quotaRowExists=false`，**不创建** quota 行。
- **写操作**（set total / grant / reset used）：**创建**当前周期 quota 行后再写入：
  - set total：创建 `creditsTotal=request, creditsUsed=0`
  - grant：创建 `creditsTotal=30+credits, creditsUsed=0`
  - reset used：创建默认 `creditsTotal=30, creditsUsed=0`

### 写操作规则

- **set total**：`creditsTotal` 范围 0~100000；若 `creditsTotal < 当前 creditsUsed` 则拒绝；不修改 `creditsUsed`。
- **grant**：`credits` 范围 1~100000；`creditsTotal += credits`，不修改 `creditsUsed`。
- **reset used**：`creditsUsed = 0`，不修改 `creditsTotal`。

### 安全

- 所有返回的 `AdminQuotaDto` 不含 `password` / `apiKey` / `encryptedKey` / `maskedKey` / `baseUrl` / `model`。
- 不修改 AI Provider 调用链、扣费逻辑、rate limit、usage records；本阶段仅管理端手动调整 `ai_usage_quota` 表。
- 并发插入通过 `(user_id, period_start)` 唯一约束 + 失败重查兜底（与 `AiUsageGuard.getOrCreateCurrentQuota` 一致）。

---

## 6. Admin permissions

Phase 8C 引入轻量后台权限增强。系统仍保留 USER / ADMIN 两级基础角色，仅在 ADMIN 内部做权限细分，不引入完整 RBAC 表（无 `roles` / `permissions` / `user_roles` / `role_permissions`）。

### 6.1 权限枚举

| 权限名 | 说明 |
|---|---|
| `ADMIN_USERS_VIEW` | 查看用户列表 / 用户详情 |
| `ADMIN_USERS_MANAGE` | 创建用户 / 修改角色 / 禁用 / 启用 |
| `ADMIN_USERS_RESET_PASSWORD` | 重置用户密码 |
| `ADMIN_AI_USAGE_VIEW` | 查看 AI usage 统计后台 |
| `ADMIN_QUOTA_VIEW` | 查看用户 quota |
| `ADMIN_QUOTA_MANAGE` | 调整用户 quota（setTotal / grant / resetUsed） |
| `ADMIN_PERMISSIONS_MANAGE` | 分配 / 修改其他 ADMIN 的 permissions（最高风险权限） |

### 6.2 数据库表

```sql
CREATE TABLE IF NOT EXISTS admin_user_permissions (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    permission  VARCHAR(80)  NOT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_admin_user_permission (user_id, permission),
    INDEX idx_admin_user_permissions_user_id (user_id),
    INDEX idx_admin_user_permissions_permission (permission)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 6.3 兼容模式（explicit permission mode）

- **表为空** → 兼容模式：所有 ADMIN 拥有全部权限（老部署升级不会把管理员锁在后台外）。
- **表非空** → 显式模式：ADMIN 需有对应 permission 才能访问对应模块。

判断逻辑：`AdminPermissionService.isExplicitPermissionMode()` 通过 `count(*) FROM admin_user_permissions` 是否为 0 决定。

### 6.4 权限规则

- **USER 永远无后台权限**，无论 `admin_user_permissions` 表里是否有记录。
- **ADMIN + 兼容模式** → 拥有全部权限。
- **ADMIN + 显式模式** → 按表判断权限。
- **role 以数据库为准**，不信任 JWT 里的 role（JWT role 只用于路由初筛）。

### 6.5 权限管理接口

路径前缀 `/api/admin/permissions`：

- **list all** — `GET /api/admin/permissions`（需 `ADMIN_PERMISSIONS_MANAGE`）
- **my permissions** — `GET /api/admin/permissions/me`（所有 ADMIN 可访问，供前端 NavBar 显示菜单）
- **get user permissions** — `GET /api/admin/permissions/users/{userId}`（需 `ADMIN_PERMISSIONS_MANAGE`）
- **update user permissions** — `PUT /api/admin/permissions/users/{userId}`（body: `{ "permissions": [...] }`，需 `ADMIN_PERMISSIONS_MANAGE`）

### 6.6 已有 Admin 接口的精细权限映射

| 接口 | 所需权限 |
|---|---|
| `GET /api/admin/users` / `GET /api/admin/users/{id}` | `ADMIN_USERS_VIEW` |
| `POST /api/admin/users` / `PUT /api/admin/users/{id}/role` / `PUT /api/admin/users/{id}/disable` / `PUT /api/admin/users/{id}/enable` | `ADMIN_USERS_MANAGE` |
| `POST /api/admin/users/{id}/reset-password` | `ADMIN_USERS_RESET_PASSWORD` |
| `GET /api/admin/ai-usage/summary` / `GET /api/admin/ai-usage/recent` | `ADMIN_AI_USAGE_VIEW` |
| `GET /api/admin/quotas` / `GET /api/admin/quotas/users/{userId}` | `ADMIN_QUOTA_VIEW` |
| `PUT /api/admin/quotas/users/{userId}/total` / `POST /api/admin/quotas/users/{userId}/grant` / `POST /api/admin/quotas/users/{userId}/reset-used` | `ADMIN_QUOTA_MANAGE` |

### 6.7 安全规则

`AdminPermissionService.updateUserPermissions(currentAdminId, targetUserId, permissions)` 强制执行：

1. **只有 ADMIN 可以拥有权限** — target 非 ADMIN 时拒绝设置。
2. **不能移除自己的 `ADMIN_PERMISSIONS_MANAGE`** — `currentAdminId == targetUserId` 且新权限集不含该权限时拒绝。
3. **不能移除系统中最后一个 `ADMIN_PERMISSIONS_MANAGE`** — target 当前持有该权限、系统中只有 1 个 manager、且新权限集移除时拒绝。
4. **显式模式启动保护** — 更新后全系统不允许没有任何 `ADMIN_PERMISSIONS_MANAGE`（包括从空表首次插入场景）。
5. **权限白名单** — 传入权限必须全部存在于 `AdminPermission` 枚举中，非法值拒绝。

更新策略：先 `DELETE FROM admin_user_permissions WHERE user_id = ?` 再逐条 `INSERT`。

### 6.8 前端

- **API**：`frontend/src/api/adminPermissions.js`（`getMyPermissions` / `listPermissions` / `getUserPermissions` / `updateUserPermissions`）。
- **Store**：`frontend/src/stores/auth.js` 新增 `adminPermissions` ref + `loadAdminPermissions()` 懒加载 + `hasAdminPermission(perm)` helper；`logout()` 时清空。
- **NavBar**：用户下拉与移动端菜单按权限显示「用户管理 / 额度管理 / AI 使用统计」入口（需对应 `*_VIEW` 权限）；mounted 时懒加载当前 ADMIN 权限。
- **AdminUsersView**：操作列新增「权限」按钮（仅当前 ADMIN 持有 `ADMIN_PERMISSIONS_MANAGE` 时显示），点击打开权限配置 Dialog（7 个 checkbox + 显式模式状态 + 安全规则提示），保存调用 `PUT /admin/permissions/users/{userId}`。
- **前端权限仅做 UX 隐藏**，后端 `/admin/**` 接口仍会做精细权限校验兜底。

