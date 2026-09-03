package com.xhhao.insight.storage.capacity;

import com.xhhao.insight.storage.database.SqliteConnectionManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;

/** Protects historical writes by measuring live and physical SQLite storage usage. */
@Component
public class StorageCapacityGuard {

    private static final long MEGABYTE = 1_024L * 1_024L;

    private final SqliteConnectionManager connectionManager;

    public StorageCapacityGuard(SqliteConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    public long currentBytes() {
        Path database = connectionManager.databasePath();
        return List.of(database, Path.of(database + "-wal"), Path.of(database + "-shm"))
            .stream()
            .mapToLong(StorageCapacityGuard::fileSize)
            .sum();
    }

    public boolean exceedsMegabytes(int limitMegabytes) {
        long safeLimit = Math.max(1, limitMegabytes) * MEGABYTE;
        return logicalBytes() >= safeLimit;
    }

    /** Returns live pages, excluding pages SQLite can immediately reuse after retention. */
    public long logicalBytes() {
        return connectionManager.read(connection -> {
            try (var statement = connection.createStatement();
                 var resultSet = statement.executeQuery("""
                     SELECT page_count, freelist_count, page_size
                     FROM pragma_page_count(), pragma_freelist_count(), pragma_page_size()
                     """)) {
                if (!resultSet.next()) {
                    throw new java.sql.SQLException("SQLite capacity PRAGMAs returned no row");
                }
                long livePages = Math.max(
                    0, resultSet.getLong("page_count") - resultSet.getLong("freelist_count"));
                try {
                    return Math.multiplyExact(livePages, resultSet.getLong("page_size"));
                } catch (ArithmeticException overflow) {
                    return Long.MAX_VALUE;
                }
            }
        });
    }

    private static long fileSize(Path path) {
        try {
            return Files.exists(path) ? Files.size(path) : 0;
        } catch (IOException ignored) {
            return 0;
        }
    }
}
