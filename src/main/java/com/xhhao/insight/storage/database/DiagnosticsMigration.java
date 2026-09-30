package com.xhhao.insight.storage.database;

import java.sql.Connection;
import java.sql.SQLException;

/** Bounded diagnostic documents and a separately indexed notification outbox. */
final class DiagnosticsMigration {
    private DiagnosticsMigration() {}

    static void apply(Connection connection) throws SQLException {
        try (var sql = connection.createStatement()) {
            sql.execute("""
                CREATE TABLE IF NOT EXISTS diagnostic_record (
                    kind TEXT NOT NULL, id TEXT NOT NULL, updated_at_ms INTEGER NOT NULL,
                    payload TEXT NOT NULL, PRIMARY KEY(kind, id))
                """);
            sql.execute("CREATE INDEX IF NOT EXISTS idx_diagnostic_time ON diagnostic_record(kind, updated_at_ms)");
            sql.execute("""
                CREATE TABLE IF NOT EXISTS notification_delivery (
                    id TEXT PRIMARY KEY, channel TEXT NOT NULL, payload TEXT NOT NULL,
                    state TEXT NOT NULL, attempts INTEGER NOT NULL, next_at_ms INTEGER NOT NULL,
                    created_at_ms INTEGER NOT NULL, updated_at_ms INTEGER NOT NULL,
                    message TEXT NOT NULL)
                """);
            sql.execute("CREATE INDEX IF NOT EXISTS idx_delivery_due ON notification_delivery(state, next_at_ms)");
        }
    }
}
