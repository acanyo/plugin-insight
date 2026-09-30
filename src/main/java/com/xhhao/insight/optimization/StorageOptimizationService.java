package com.xhhao.insight.optimization;

import com.xhhao.insight.storage.database.SqliteConnectionManager;
import java.sql.Statement;
import org.springframework.stereotype.Component;

/** Runs SQLite's non-destructive planner/statistics maintenance after optimization changes. */
@Component
public class StorageOptimizationService {

    private final SqliteConnectionManager connectionManager;

    public StorageOptimizationService(SqliteConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    public void optimize() {
        connectionManager.write(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA optimize");
            }
            return null;
        });
    }
}
