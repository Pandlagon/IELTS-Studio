package com.ieltsstudio.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseMigrationRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        ensureRootMemoryColumn();
        ensureExampleTranslationColumn();
        ensureWordStudyStatesTable();
    }

    private void ensureRootMemoryColumn() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'word_entries'
                  AND COLUMN_NAME = 'root_memory'
                """, Integer.class);

        if (count != null && count > 0) return;

        jdbcTemplate.execute("ALTER TABLE word_entries ADD COLUMN root_memory TEXT AFTER example");
        log.info("Database migration applied: added word_entries.root_memory");
    }

    private void ensureExampleTranslationColumn() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'word_entries'
                  AND COLUMN_NAME = 'example_translation'
                """, Integer.class);

        if (count != null && count > 0) return;

        jdbcTemplate.execute("ALTER TABLE word_entries ADD COLUMN example_translation TEXT AFTER example");
        log.info("Database migration applied: added word_entries.example_translation");
    }

    private void ensureWordStudyStatesTable() {
        jdbcTemplate.execute("""
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        log.info("Database migration checked: word_study_states");
    }
}
