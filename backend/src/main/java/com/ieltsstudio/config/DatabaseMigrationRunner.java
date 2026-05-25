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
}
