package com.xhhao.insight.storage.database;

import com.xhhao.insight.storage.StorageException;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DatabaseRecoveryManager {

    private final SqliteConnectionManager connectionManager;
    private final SchemaMigrator schemaMigrator;
    private final Clock clock;

    @Autowired
    public DatabaseRecoveryManager(
        SqliteConnectionManager connectionManager,
        SchemaMigrator schemaMigrator
    ) {
        this(connectionManager, schemaMigrator, Clock.systemUTC());
    }

    DatabaseRecoveryManager(
        SqliteConnectionManager connectionManager,
        SchemaMigrator schemaMigrator,
        Clock clock
    ) {
        this.connectionManager = connectionManager;
        this.schemaMigrator = schemaMigrator;
        this.clock = clock;
    }

    public void verifyQuickCheck() {
        List<String> failures = connectionManager.read(connection -> {
            List<String> messages = new ArrayList<>();
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("PRAGMA quick_check")) {
                while (resultSet.next()) {
                    String message = resultSet.getString(1);
                    if (!"ok".equalsIgnoreCase(message)) {
                        messages.add(message);
                    }
                }
            }
            return messages;
        });
        if (!failures.isEmpty()) {
            throw new DatabaseCorruptionException(
                "SQLite quick_check failed: " + String.join("; ", failures));
        }
    }

    public Path quarantineAndRecreate() {
        return quarantineAndRecreate("corrupt");
    }

    public Path quarantineInvalidSchemaAndRecreate() {
        return quarantineAndRecreate("invalid");
    }

    private Path quarantineAndRecreate(String category) {
        Path databasePath = connectionManager.databasePath();
        connectionManager.closeWriteConnection();
        Path quarantineDirectory = databasePath.getParent().resolve(category);
        String baseName = "insight-" + clock.millis() + ".sqlite";
        Path quarantinedDatabase = quarantineDirectory.resolve(baseName);
        try {
            Files.createDirectories(quarantineDirectory);
            moveIfExists(databasePath, quarantinedDatabase);
            moveIfExists(Path.of(databasePath + "-wal"), Path.of(quarantinedDatabase + "-wal"));
            moveIfExists(Path.of(databasePath + "-shm"), Path.of(quarantinedDatabase + "-shm"));
        } catch (IOException e) {
            throw new StorageException("Failed to quarantine corrupt SQLite database", e);
        }

        connectionManager.open();
        schemaMigrator.migrate();
        verifyQuickCheck();
        return quarantinedDatabase;
    }

    public boolean isConfirmedCorruption(Throwable failure) {
        return failure instanceof DatabaseCorruptionException
            || SqliteConnectionManager.isCorruption(failure);
    }

    private static void moveIfExists(Path source, Path target) throws IOException {
        if (!Files.exists(source)) {
            return;
        }
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target);
        }
    }
}
