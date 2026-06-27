# 数据库改动指南

> 本文件说明 IELTS Studio 数据库改动的规范与流程。
> 配套阅读：[ai-provider-architecture.md](./ai-provider-architecture.md)、[security-and-quota-plan.md](./security-and-quota-plan.md)。

---

## 1. 当前数据库初始化文件

- 位置：`backend/src/main/resources/db/init.sql`
- 数据库名：`ielts_studio`
- 字符集：`utf8mb4` / `utf8mb4_unicode_ci`
- 现有表：`users`、`exams`、`exam_sections`、`questions`、`exam_records`、`error_book`、`word_books`、`word_entries`、`word_progress`、`word_study_states`、`exam_collections`、`exam_collection_items`、`study_checkins`
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

## 5. 未来 `user_ai_settings` 表建议字段

> 本节仅为设计，**这一步不创建表**。

```sql
CREATE TABLE IF NOT EXISTS user_ai_settings (
    id                          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id                     BIGINT       NOT NULL,
    key_mode                    VARCHAR(20)  NOT NULL DEFAULT 'BUILTIN',  -- BUILTIN / USER

    -- Text Provider 配置（USER 模式生效）
    text_provider               VARCHAR(50),                    -- DeepSeek / CUSTOM
    text_base_url               VARCHAR(500),
    text_model                  VARCHAR(100),
    text_api_key_encrypted      VARCHAR(1000),
    text_api_key_masked         VARCHAR(50),

    -- Vision Provider 配置（USER 模式生效）
    vision_provider             VARCHAR(50),                    -- Qwen / MiMO / CUSTOM
    vision_base_url             VARCHAR(500),
    vision_model                VARCHAR(100),
    vision_api_key_encrypted    VARCHAR(1000),
    vision_api_key_masked       VARCHAR(50),

    created_at                  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_uas_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

字段说明：

| 字段 | 说明 |
|---|---|
| `user_id` | 关联 `users.id`，一对一 |
| `key_mode` | `BUILTIN`（用站点 Key）或 `USER`（用自填 Key） |
| `text_provider` | 文本 Provider 标识，预设或 `CUSTOM` |
| `text_base_url` | 自定义文本 Base URL（OpenAI-compatible） |
| `text_model` | 文本模型名 |
| `text_api_key_encrypted` | 文本 API Key 加密密文，**不返回前端** |
| `text_api_key_masked` | 文本 API Key 脱敏串，可返回前端展示 |
| `vision_*` | 同上，对应多模态任务 |
| `created_at` / `updated_at` | 标准时间字段 |

---

## 6. 未来 `ai_usage_quota` / `ai_usage_records` 表设计方向

> 本节仅为设计，**这一步不创建表**。

两条路线，实施时择一（或结合）：

### 路线 A：`ai_usage_quota`（额度快照表）

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
    UNIQUE KEY uk_uq_user_period (user_id, period_start),
    INDEX idx_uq_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 路线 B：`ai_usage_records`（扣费流水表）

记录每次 AI 调用扣费明细，适合审计与统计，剩余额度按流水聚合。

```sql
CREATE TABLE IF NOT EXISTS ai_usage_records (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    task_type       VARCHAR(20)  NOT NULL,        -- TEXT / VISION
    feature         VARCHAR(50)  NOT NULL,        -- grade-writing / ai-chat / translate / ...
    cost            INT          NOT NULL,
    key_mode        VARCHAR(20)  NOT NULL,        -- BUILTIN / USER
    provider        VARCHAR(50),
    status          VARCHAR(20)  NOT NULL,        -- SUCCESS / FAILED
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ur_user_id (user_id),
    INDEX idx_ur_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 建议

- 仅做额度校验：选 A，简单高效。
- 需要审计/统计/按功能分析用量：选 B，或 A+B（A 做快照、B 做流水）。
- 实施时在 PR 中确定最终方案并更新本文件。

---

## 7. 改动流程总结

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

> 这一步（Phase 1）只写文档，**不要**真的创建 `user_ai_settings` / `ai_usage_quota` / `ai_usage_records` 表。
