CREATE DATABASE IF NOT EXISTS ielts_studio DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE ielts_studio;

-- Users
CREATE TABLE IF NOT EXISTS users (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(50)  NOT NULL UNIQUE,
    email       VARCHAR(100) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    avatar      VARCHAR(500),
    role        VARCHAR(20)  NOT NULL DEFAULT 'USER',
    deleted     TINYINT(1)   NOT NULL DEFAULT 0,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username (username),
    INDEX idx_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Exams
CREATE TABLE IF NOT EXISTS exams (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT       NOT NULL,
    title          VARCHAR(255) NOT NULL,
    description    VARCHAR(500),
    type           VARCHAR(20)  NOT NULL DEFAULT 'reading',
    status         VARCHAR(20)  NOT NULL DEFAULT 'processing',
    question_count INT          NOT NULL DEFAULT 0,
    duration       INT          NOT NULL DEFAULT 60,
    difficulty     VARCHAR(20)  DEFAULT '中等',
    tags           VARCHAR(500) DEFAULT NULL COMMENT 'JSON array of tags, e.g. ["Academic","Writing","Task 2"]',
    file_key       VARCHAR(500),
    parse_result   LONGTEXT,
    deleted        TINYINT(1)   NOT NULL DEFAULT 0,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Exam Sections
CREATE TABLE IF NOT EXISTS exam_sections (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    exam_id    BIGINT       NOT NULL,
    title      VARCHAR(255),
    passage    LONGTEXT,
    sort_order INT          NOT NULL DEFAULT 0,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_exam_id (exam_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Questions
CREATE TABLE IF NOT EXISTS questions (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    exam_id         BIGINT       NOT NULL,
    section_id      BIGINT,
    question_number INT          NOT NULL,
    type            VARCHAR(20)  NOT NULL DEFAULT 'fill',
    question_text   TEXT         NOT NULL,
    options         JSON,
    answer          TEXT         NOT NULL,
    explanation     TEXT,
    locator_text    TEXT,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_exam_id (exam_id),
    INDEX idx_section_id (section_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Exam Records
CREATE TABLE IF NOT EXISTS exam_records (
    id            BIGINT    NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT    NOT NULL,
    exam_id       BIGINT    NOT NULL,
    correct_count INT       NOT NULL DEFAULT 0,
    total_count   INT       NOT NULL DEFAULT 0,
    band_score    DOUBLE    NOT NULL DEFAULT 0,
    time_used     INT,
    answers_json       LONGTEXT,
    ai_feedback_json   LONGTEXT,
    submitted_at       DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_exam_id (exam_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Migration: run this if exams already exists without tags column
-- ALTER TABLE exams ADD COLUMN tags VARCHAR(500) DEFAULT NULL COMMENT 'JSON array of tags' AFTER difficulty;

-- Migration: run this if exam_records already exists without ai_feedback_json
-- ALTER TABLE exam_records ADD COLUMN ai_feedback_json LONGTEXT AFTER answers_json;

-- Error Book
CREATE TABLE IF NOT EXISTS error_book (
    id             BIGINT     NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT     NOT NULL,
    exam_id        BIGINT     NOT NULL,
    question_id    BIGINT     NOT NULL,
    user_answer    VARCHAR(500),
    correct_answer VARCHAR(500),
    review_count   INT        NOT NULL DEFAULT 0,
    mastered       TINYINT(1) NOT NULL DEFAULT 0,
    created_at     DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_question_id (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Word Books
CREATE TABLE IF NOT EXISTS word_books (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(300),
    is_default  TINYINT(1)   NOT NULL DEFAULT 0,
    word_count  INT          NOT NULL DEFAULT 0,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ready',
    deleted     TINYINT(1)   NOT NULL DEFAULT 0,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_wb_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Word Entries
CREATE TABLE IF NOT EXISTS word_entries (
    id        BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    book_id   BIGINT       NOT NULL,
    user_id   BIGINT       NOT NULL,
    word      VARCHAR(200) NOT NULL,
    phonetic  VARCHAR(200),
    pos       VARCHAR(50),
    pos_type  VARCHAR(20),
    meaning   VARCHAR(500) NOT NULL,
    example   TEXT,
    example_translation TEXT,
    root_memory TEXT,
    deleted   TINYINT(1)   NOT NULL DEFAULT 0,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_we_book_id (book_id),
    INDEX idx_we_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Migration: run if word_books already exists without status
-- ALTER TABLE word_books ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ready' AFTER word_count;
-- Migration: run if word_entries already exists without root_memory
-- ALTER TABLE word_entries ADD COLUMN root_memory TEXT AFTER example;
-- Migration: run if word_entries already exists without example_translation
-- ALTER TABLE word_entries ADD COLUMN example_translation TEXT AFTER example;

-- Word Progress
CREATE TABLE IF NOT EXISTS word_progress (
    id         BIGINT     NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT     NOT NULL,
    word_id    INT        NOT NULL,
    word       VARCHAR(100),
    status     VARCHAR(20) NOT NULL DEFAULT 'new',  -- new, known, unknown
    review_count INT      NOT NULL DEFAULT 0,
    next_review  DATETIME,
    created_at DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_word (user_id, word_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS word_study_states (
    id            BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT      NOT NULL,
    book_id       VARCHAR(50) NOT NULL,
    known_ids     TEXT,
    unknown_ids   TEXT,
    review_states MEDIUMTEXT,
    error_counts  TEXT,
    sort_mode     VARCHAR(20) NOT NULL DEFAULT 'order',
    batch_size    INT         NOT NULL DEFAULT 0,
    batch_index   INT         NOT NULL DEFAULT 0,
    current_index INT         NOT NULL DEFAULT 0,
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_book (user_id, book_id),
    INDEX idx_wss_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Exam Collections (试卷集)
CREATE TABLE IF NOT EXISTS exam_collections (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    title       VARCHAR(255) NOT NULL,
    description VARCHAR(500),
    duration    INT          NOT NULL DEFAULT 0,
    deleted     TINYINT(1)   NOT NULL DEFAULT 0,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_ec_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Exam Collection Items (试卷集内的试卷)
CREATE TABLE IF NOT EXISTS exam_collection_items (
    id            BIGINT   NOT NULL AUTO_INCREMENT PRIMARY KEY,
    collection_id BIGINT   NOT NULL,
    exam_id       BIGINT   NOT NULL,
    sort_order    INT      NOT NULL DEFAULT 0,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_collection_exam (collection_id, exam_id),
    INDEX idx_eci_collection (collection_id),
    INDEX idx_eci_exam (exam_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Study Check-ins
CREATE TABLE IF NOT EXISTS study_checkins (
    id           BIGINT   NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT   NOT NULL,
    checkin_date DATE     NOT NULL,
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_date (user_id, checkin_date),
    INDEX idx_sc_user_id (user_id),
    INDEX idx_sc_date (checkin_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ─────────────────────────────────────────────────────────────────────────────
-- Phase 3A：AI 用户设置与额度数据库基础设施
-- 以下三张表为用户自填 API Key / 内置额度 / AI 调用流水提供存储。
-- 仅建表 + Entity + Mapper，业务逻辑（扣费 / 解析用户设置）留待后续阶段。
-- 敏感字段（*_api_key_encrypted）必须经后端加密后入库，*_api_key_masked 供前端展示。
-- ─────────────────────────────────────────────────────────────────────────────

-- User AI Settings（每个用户一份 AI 使用模式与自填 Provider 配置）
CREATE TABLE IF NOT EXISTS user_ai_settings (
    id                          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id                     BIGINT       NOT NULL,
    key_mode                    VARCHAR(20)  NOT NULL DEFAULT 'BUILTIN',  -- BUILTIN / USER

    -- Text Provider 配置（USER 模式生效）
    text_provider               VARCHAR(50)  NOT NULL DEFAULT 'DEEPSEEK', -- DEEPSEEK / OPENAI_COMPATIBLE
    text_base_url               VARCHAR(500),
    text_model                  VARCHAR(100),
    text_api_key_encrypted      VARCHAR(1000),                             -- 加密密文，禁止返回前端
    text_api_key_masked         VARCHAR(50),                               -- 脱敏串，如 sk-****abcd

    -- Vision Provider 配置（USER 模式生效）
    vision_provider             VARCHAR(50)  NOT NULL DEFAULT 'QWEN',      -- QWEN / MIMO / OPENAI_COMPATIBLE
    vision_base_url             VARCHAR(500),
    vision_model                VARCHAR(100),
    vision_api_key_encrypted    VARCHAR(1000),                             -- 加密密文，禁止返回前端
    vision_api_key_masked       VARCHAR(50),                               -- 脱敏串，如 sk-****abcd

    created_at                  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_user_ai_settings_user_id (user_id),
    INDEX idx_user_ai_settings_key_mode (key_mode)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- AI Usage Quota（用户当前周期的内置额度快照）
CREATE TABLE IF NOT EXISTS ai_usage_quota (
    id              BIGINT   NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT   NOT NULL,
    period_start    DATETIME NOT NULL,
    period_end      DATETIME NOT NULL,
    credits_total   INT      NOT NULL DEFAULT 30,
    credits_used    INT      NOT NULL DEFAULT 0,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_ai_usage_quota_user_period (user_id, period_start),
    INDEX idx_ai_usage_quota_user_id (user_id),
    INDEX idx_ai_usage_quota_period_end (period_end)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- AI Usage Records（AI 调用流水，用于审计 / 调试 / 统计）
CREATE TABLE IF NOT EXISTS ai_usage_records (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    task_type       VARCHAR(20)  NOT NULL,   -- TEXT / VISION
    feature         VARCHAR(50)  NOT NULL,   -- grade-writing / ai-chat / translate / ...
    cost            INT          NOT NULL DEFAULT 0,
    key_mode        VARCHAR(20)  NOT NULL,   -- BUILTIN / USER
    provider        VARCHAR(50),
    status          VARCHAR(20)  NOT NULL,   -- SUCCESS / FAILED / REJECTED
    error_message   VARCHAR(500),            -- 脱敏后的简短错误摘要；禁止记录 API Key
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_ai_usage_records_user_id (user_id),
    INDEX idx_ai_usage_records_created_at (created_at),
    INDEX idx_ai_usage_records_feature (feature),
    INDEX idx_ai_usage_records_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Migration: run these if upgrading an existing deployment without AI settings tables
-- (see table definitions above)
-- CREATE TABLE IF NOT EXISTS user_ai_settings (...);
-- CREATE TABLE IF NOT EXISTS ai_usage_quota (...);
-- CREATE TABLE IF NOT EXISTS ai_usage_records (...);

-- ─────────────────────────────────────────────────────────────────────────────
-- Phase 8C：Admin 轻量权限
-- admin_user_permissions 记录 ADMIN 账号被分配的后台细粒度权限。
-- 兼容策略：表为空 → 所有 ADMIN 拥有全部权限（兼容老部署）；
--           表非空 → 进入显式权限模式，ADMIN 需有对应 permission 才能访问对应模块。
-- 仅 USER/ADMIN 两级基础角色不变；本表只对 ADMIN 内部做权限细分。
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS admin_user_permissions (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    permission  VARCHAR(80)  NOT NULL,                     -- AdminPermission 枚举名
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uk_admin_user_permission (user_id, permission),
    INDEX idx_admin_user_permissions_user_id (user_id),
    INDEX idx_admin_user_permissions_permission (permission)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Migration: run this if upgrading an existing deployment without admin_user_permissions table
-- CREATE TABLE IF NOT EXISTS admin_user_permissions (...);

-- ─────────────────────────────────────────────────────────────────────────────
-- Phase 8D：Admin 操作审计日志
-- admin_operation_logs 记录管理端高风险写操作（创建/修改/禁用/启用/重置密码/quota/权限）。
-- summary 字段必须脱敏，禁止放 password / API Key / token / Authorization / provider body。
-- 仅记录成功写操作；失败审计留到后续增强。
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS admin_operation_logs (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    actor_user_id   BIGINT       NOT NULL,
    actor_username  VARCHAR(100),
    action          VARCHAR(80)  NOT NULL,                -- AdminOperationAction 枚举名
    resource_type   VARCHAR(80)  NOT NULL,                -- USER / QUOTA / PERMISSION
    resource_id     BIGINT,
    target_user_id  BIGINT,
    status          VARCHAR(20)  NOT NULL,                -- SUCCESS / FAILED
    summary         VARCHAR(1000),                        -- 脱敏摘要
    ip_address      VARCHAR(80),
    user_agent      VARCHAR(300),
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_admin_operation_logs_actor_user_id (actor_user_id),
    INDEX idx_admin_operation_logs_action (action),
    INDEX idx_admin_operation_logs_resource (resource_type, resource_id),
    INDEX idx_admin_operation_logs_target_user_id (target_user_id),
    INDEX idx_admin_operation_logs_status (status),
    INDEX idx_admin_operation_logs_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Migration: run this if upgrading an existing deployment without admin_operation_logs table
-- CREATE TABLE IF NOT EXISTS admin_operation_logs (...);
