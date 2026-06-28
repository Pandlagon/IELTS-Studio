# 数据库改动指南

> 本文件说明 IELTS Studio 数据库改动的规范与流程。
> 配套阅读：[ai-provider-architecture.md](./ai-provider-architecture.md)、[security-and-quota-plan.md](./security-and-quota-plan.md)。

---

## 1. 当前数据库初始化文件

- 位置：`backend/src/main/resources/db/init.sql`
- 数据库名：`ielts_studio`
- 字符集：`utf8mb4` / `utf8mb4_unicode_ci`
- 现有表：`users`、`exams`、`exam_sections`、`questions`、`exam_records`、`error_book`、`word_books`、`word_entries`、`word_progress`、`word_study_states`、`exam_collections`、`exam_collection_items`、`study_checkins`、`user_ai_settings`、`ai_usage_quota`、`ai_usage_records`、`admin_user_permissions`
- 命名约定：表名/字段名 snake_case，时间字段 `created_at` / `updated_at`，软删除字段 `deleted TINYINT(1)`

---

## 2. 新增表

1. 在 `init.sql` 末尾追加 `CREATE TABLE IF NOT EXISTS ...`，遵循现有风格（含注释、索引、引擎）。
2. 在 `backend/.../entity/` 新建实体类，`@TableName` 对应表名。
3. 在 `backend/.../mapper/` 新建 Mapper 继承 `BaseMapper<T>`。
4. 若已有部署需增量升级，在 `init.sql` 中以注释形式写 migration note（参考现有写法）。
5. PR 描述中说明新增表用途。

---

## 3. 新增字段

1. 直接修改 `init.sql` 中对应 `CREATE TABLE` 的字段定义（保证新部署拿到最新结构）。
2. 若已有部署需要增量升级，在 `init.sql` 中以注释形式写 `ALTER TABLE` migration note，例如：
   ```sql
   -- Migration: run this if xxx already exists without yyy column
   -- ALTER TABLE xxx ADD COLUMN yyy VARCHAR(100) AFTER zzz;
   ```
3. 同步更新对应 Entity 的字段映射。
4. PR 描述中列出 migration SQL。

> 不要只改 Entity 不改 `init.sql`，否则新部署会缺字段。

---

## 4. 敏感字段规则

- **不要**把 API Key、密码、JWT secret 明文存在普通字段里。
- 用户自填 API Key 必须加密存储：
  - `*_api_key_encrypted`：加密后的密文（AES 等可逆加密，密钥由后端持有，不入库）。
  - `*_api_key_masked`：脱敏后的展示串（如 `sk-****abcd`），供前端展示。
  - **绝不**保存明文，**绝不**把 `*_api_key_encrypted` 返回前端。

---

## 5. `user_ai_settings` 表

> ✅ **已于 Phase 3A 实现**，建表 SQL 见 `backend/src/main/resources/db/init.sql`。
> 对应 Entity：`com.ieltsstudio.entity.UserAiSettings`；Mapper：`UserAiSettingsMapper`。
> 业务逻辑（解析用户设置 / 解密 Key）留待后续阶段。

```sql
CREATE TABLE IF NOT EXISTS user_ai_settings (
    id                          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id                     BIGINT       NOT NULL,
    key_mode                    VARCHAR(20)  NOT NULL DEFAULT 'BUILTIN',  -- BUILTIN / USER

    -- Text Provider 配置（USER 模式生效）
    text_provider               VARCHAR(50)  NOT NULL DEFAULT 'DEEPSEEK', -- DEEPSEEK / OPENAI_COMPATIBLE
    text_base_url               VARCHAR(500),
    text_model                  VARCHAR(100),
    text_api_key_encrypted      VARCHAR(1000),
    text_api_key_masked         VARCHAR(50),

    -- Vision Provider 配置（USER 模式生效）
    vision_provider             VARCHAR(50)  NOT NULL DEFAULT 'QWEN',      -- QWEN / MIMO / OPENAI_COMPATIBLE
    vision_base_url             VARCHAR(500),
    vision_model                VARCHAR(100),
    vision_api_key_encrypted    VARCHAR(1000),
    vision_api_key_masked       VARCHAR(50),

    created_at                  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_ai_settings_user_id (user_id),
    INDEX idx_user_ai_settings_key_mode (key_mode)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

字段说明：

| 字段 | 说明 |
|---|---|
| `user_id` | 关联 `users.id`，一对一 |
| `key_mode` | `BUILTIN`（用站点 Key）或 `USER`（用自填 Key） |
| `text_provider` | 文本 Provider 标识，预设或 `OPENAI_COMPATIBLE` |
| `text_base_url` | 自定义文本 Base URL（OpenAI-compatible） |
| `text_model` | 文本模型名 |
| `text_api_key_encrypted` | 文本 API Key 加密密文，**不返回前端** |
| `text_api_key_masked` | 文本 API Key 脱敏串，可返回前端展示 |
| `vision_*` | 同上，对应多模态任务 |
| `created_at` / `updated_at` | 标准时间字段 |

---

## 6. `ai_usage_quota` / `ai_usage_records` 表

> ✅ **已于 Phase 3A 实现（A+B 结合方案）**，建表 SQL 见 `backend/src/main/resources/db/init.sql`。
> 对应 Entity：`AiUsageQuota` / `AiUsageRecord`；Mapper：`AiUsageQuotaMapper` / `AiUsageRecordMapper`。
> 扣费 / 周期切换 / 统计 SQL 留待后续阶段（Phase 3B/3C）。

### `ai_usage_quota`（额度快照表）

记录每用户当前周期剩余 credits，适合高频读、低频写。

```sql
CREATE TABLE IF NOT EXISTS ai_usage_quota (
    id              BIGINT   NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT   NOT NULL,
    period_start    DATETIME NOT NULL,           -- 周期开始时间
    period_end      DATETIME NOT NULL,           -- 周期结束时间
    credits_total   INT      NOT NULL DEFAULT 30,-- 周期发放额度
    credits_used    INT      NOT NULL DEFAULT 0, -- 已使用额度
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ai_usage_quota_user_period (user_id, period_start),
    INDEX idx_ai_usage_quota_user_id (user_id),
    INDEX idx_ai_usage_quota_period_end (period_end)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### `ai_usage_records`（扣费流水表）

记录每次 AI 调用明细，适合审计与统计，剩余额度按流水聚合。

```sql
CREATE TABLE IF NOT EXISTS ai_usage_records (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    task_type       VARCHAR(20)  NOT NULL,        -- TEXT / VISION
    feature         VARCHAR(50)  NOT NULL,        -- grade-writing / ai-chat / translate / ...
    cost            INT          NOT NULL DEFAULT 0,
    key_mode        VARCHAR(20)  NOT NULL,        -- BUILTIN / USER
    provider        VARCHAR(50),
    status          VARCHAR(20)  NOT NULL,        -- SUCCESS / FAILED / REJECTED
    error_message   VARCHAR(500),                 -- 脱敏后的简短错误摘要；禁止记录 API Key
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ai_usage_records_user_id (user_id),
    INDEX idx_ai_usage_records_created_at (created_at),
    INDEX idx_ai_usage_records_feature (feature),
    INDEX idx_ai_usage_records_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 方案选择

- 最终采用 **A+B 结合**：A 做额度快照（高频读、O(1) 校验余量），B 做调用流水（审计 / 统计 / 按功能分析用量）。
- 本阶段仅建表与 Entity/Mapper，扣费逻辑留待后续阶段。

---

## 7. `admin_user_permissions` 表

> ✅ **已于 Phase 8C 实现**，建表 SQL 见 `backend/src/main/resources/db/init.sql`。
> 对应 Entity：`com.ieltsstudio.entity.AdminUserPermission` + 枚举 `com.ieltsstudio.entity.AdminPermission`；
> Mapper：`AdminUserPermissionMapper`；Service：`AdminPermissionService`。

```sql
CREATE TABLE IF NOT EXISTS admin_user_permissions (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    permission  VARCHAR(80)  NOT NULL,                     -- AdminPermission 枚举名
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uk_admin_user_permission (user_id, permission),
    INDEX idx_admin_user_permissions_user_id (user_id),
    INDEX idx_admin_user_permissions_permission (permission)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

字段说明：

| 字段 | 说明 |
|---|---|
| `user_id` | 关联 `users.id`，目标必须是 `role=ADMIN` |
| `permission` | `AdminPermission` 枚举名（如 `ADMIN_USERS_VIEW` / `ADMIN_PERMISSIONS_MANAGE`） |
| `created_at` | 标准时间字段（无 `updated_at`，权限记录不可修改，只能先删后插） |
| `uk_admin_user_permission` | `(user_id, permission)` 唯一约束防重复 |

### 兼容模式（explicit permission mode）

- **表为空** → 兼容模式：所有 ADMIN 拥有全部权限（老部署升级不会把管理员锁在后台外）。
- **表非空** → 显式模式：ADMIN 需有对应 permission 才能访问对应模块。
- 判断逻辑：`AdminPermissionService.isExplicitPermissionMode()` 通过 `count(*) FROM admin_user_permissions` 是否为 0 决定。

### 安全规则

`AdminPermissionService.updateUserPermissions(...)` 强制执行 5 条规则：
1. 只有 ADMIN 可以拥有权限（target 非 ADMIN 拒绝）。
2. 不能移除自己的 `ADMIN_PERMISSIONS_MANAGE`。
3. 不能移除系统中最后一个 `ADMIN_PERMISSIONS_MANAGE`。
4. 显式模式启动保护：更新后全系统不允许没有任何 `ADMIN_PERMISSIONS_MANAGE`。
5. 权限白名单：传入权限必须全部存在于 `AdminPermission` 枚举中。

### Migration note

已有部署升级时执行（已在 `init.sql` 末尾以注释形式保留）：

```sql
-- Migration: run this if upgrading an existing deployment without admin_user_permissions table
-- CREATE TABLE IF NOT EXISTS admin_user_permissions (...);
```

---

## 8. 改动流程总结

```
改 init.sql（CREATE / ALTER）
   │
   ├── 新表/字段 → 新建/更新 Entity + Mapper
   │
   ├── 已有部署 → 在 init.sql 写 migration note（注释式 ALTER）
   │
   ├── 敏感字段 → 加密存储 + masked 字段，不明文、不返回明文
   │
   └── PR 描述中列出 migration SQL + 用途说明
```

> Phase 1 阶段只写文档；`user_ai_settings` / `ai_usage_quota` / `ai_usage_records` 三张表已于 **Phase 3A** 真正落地（见 `init.sql`）。
