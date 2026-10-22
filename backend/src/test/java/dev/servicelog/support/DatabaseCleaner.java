package dev.servicelog.support;

import org.springframework.jdbc.core.JdbcTemplate;

// Empties every application table between integration tests, in one statement, so tests
// don't depend on the order they run in or on foreign keys between tables.
public final class DatabaseCleaner {

    private DatabaseCleaner() {
    }

    public static void reset(JdbcTemplate jdbc) {
        jdbc.execute("TRUNCATE TABLE service_record_photo, tune_up_reminder, job, service_record, equipment, property, customer, auth_token, app_user "
                + "RESTART IDENTITY CASCADE");
    }
}
