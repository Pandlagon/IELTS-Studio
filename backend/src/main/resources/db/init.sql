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
    answer          VARCHAR(500) NOT NULL,
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
    deleted   TINYINT(1)   NOT NULL DEFAULT 0,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_we_book_id (book_id),
    INDEX idx_we_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Migration: run if word_books already exists without status
-- ALTER TABLE word_books ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ready' AFTER word_count;

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
