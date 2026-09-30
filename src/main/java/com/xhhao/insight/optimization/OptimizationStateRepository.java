package com.xhhao.insight.optimization;

import com.xhhao.insight.storage.database.SqliteConnectionManager;
import java.sql.PreparedStatement;
import org.springframework.stereotype.Repository;

/** Stores only the active runtime profile; optimization changes are audited as events. */
@Repository
public class OptimizationStateRepository {

    private final SqliteConnectionManager connectionManager;

    public OptimizationStateRepository(SqliteConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    public OptimizationProfile load() {
        return connectionManager.read(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT low_overhead_enabled, applied_at_ms, updated_at_ms
                FROM optimization_state WHERE id = 1
                """); var row = statement.executeQuery()) {
                return row.next()
                    ? new OptimizationProfile(row.getBoolean("low_overhead_enabled"),
                        row.getLong("applied_at_ms"), row.getLong("updated_at_ms"))
                    : OptimizationProfile.standard();
            }
        });
    }

    public void save(OptimizationProfile profile) {
        connectionManager.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO optimization_state(
                    id, low_overhead_enabled, applied_at_ms, updated_at_ms
                ) VALUES (1, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    low_overhead_enabled=excluded.low_overhead_enabled,
                    applied_at_ms=excluded.applied_at_ms,
                    updated_at_ms=excluded.updated_at_ms
                """)) {
                statement.setBoolean(1, profile.enabled());
                statement.setLong(2, profile.appliedAtMs());
                statement.setLong(3, profile.updatedAtMs());
                statement.executeUpdate();
            }
            return null;
        });
    }
}
