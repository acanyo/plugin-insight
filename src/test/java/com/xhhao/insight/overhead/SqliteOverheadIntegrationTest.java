package com.xhhao.insight.overhead;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xhhao.insight.storage.StorageException;
import com.xhhao.insight.storage.database.SqliteConnectionManager;
import com.xhhao.insight.storage.health.StorageHealth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

class SqliteOverheadIntegrationTest {
    @TempDir Path temporary;

    @Test
    void measuresActualDatabaseOperationsAndCountsBusyRetryAsOneRead() {
        var overhead = new InsightOverheadMetrics();
        try (var database =
                new SqliteConnectionManager(
                        temporary.resolve("insight.sqlite"), new StorageHealth(), overhead)) {
            database.open();
            database.write(
                    connection -> {
                        try (var statement = connection.createStatement()) {
                            statement.executeUpdate("CREATE TABLE evidence(value INTEGER)");
                            statement.executeUpdate("INSERT INTO evidence VALUES(7)");
                        }
                        return null;
                    });
            var attempts = new AtomicInteger();
            var value =
                    database.read(
                            connection -> {
                                if (attempts.incrementAndGet() == 1)
                                    throw new SQLException("busy", "", 5);
                                try (var statement = connection.createStatement();
                                        var rows =
                                                statement.executeQuery(
                                                        "SELECT value FROM evidence")) {
                                    rows.next();
                                    return rows.getInt(1);
                                }
                            });
            assertThat(value).isEqualTo(7);
            assertThat(attempts.get()).isEqualTo(2);
            assertThatThrownBy(
                            () ->
                                    database.read(
                                            connection -> {
                                                throw new SQLException("invalid", "", 1);
                                            }))
                    .isInstanceOf(StorageException.class);
            var snapshot = overhead.snapshot();
            assertThat(snapshot.databaseWrites()).isEqualTo(1);
            assertThat(snapshot.databaseReads()).isEqualTo(2);
            assertThat(snapshot.operations())
                    .anySatisfy(
                            operation -> {
                                assertThat(operation.id()).isEqualTo("sql_read");
                                assertThat(operation.failures()).isEqualTo(1);
                            });
        }
    }
}
