package com.xhhao.insight.storage.database;

import com.xhhao.insight.storage.health.StorageHealth;
import com.xhhao.insight.storage.health.StorageHealthSnapshot;
import com.xhhao.insight.storage.health.StorageState;
import com.xhhao.insight.runtime.FailureSummary;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Coordinates storage startup and shutdown without leaking failures into Halo lifecycle code.
 */
@Component
public class SqliteStorageLifecycle implements AutoCloseable {

    private final SqliteConnectionManager connectionManager;
    private final SchemaMigrator schemaMigrator;
    private final DatabaseRecoveryManager recoveryManager;
    private final StorageHealth health;

    @Autowired
    public SqliteStorageLifecycle(
        SqliteConnectionManager connectionManager,
        SchemaMigrator schemaMigrator,
        DatabaseRecoveryManager recoveryManager,
        StorageHealth health
    ) {
        this.connectionManager = connectionManager;
        this.schemaMigrator = schemaMigrator;
        this.recoveryManager = recoveryManager;
        this.health = health;
    }

    public SqliteStorageLifecycle(Path databasePath) {
        this.health = new StorageHealth();
        this.connectionManager = new SqliteConnectionManager(databasePath, health);
        this.schemaMigrator = new SchemaMigrator(connectionManager);
        this.recoveryManager = new DatabaseRecoveryManager(connectionManager, schemaMigrator);
    }

    public synchronized boolean initialize() {
        health.markInitializing();
        try {
            connectionManager.open();
            recoveryManager.verifyQuickCheck();
            schemaMigrator.migrate();
            health.markAvailable("SQLite storage is available");
            return true;
        } catch (SchemaVersionException e) {
            health.markIncompatible(e.getMessage());
            connectionManager.closeWriteConnection();
            return false;
        } catch (SchemaValidationException e) {
            return recoverFromInvalidSchema(e);
        } catch (RuntimeException failure) {
            if (recoveryManager.isConfirmedCorruption(failure)) {
                return recoverFromCorruption(failure);
            }
            health.markDegraded("SQLite storage initialization failed: " + safeMessage(failure));
            connectionManager.closeWriteConnection();
            return false;
        }
    }

    public StorageHealthSnapshot health() {
        return health.snapshot();
    }

    public SqliteConnectionManager connectionManager() {
        return connectionManager;
    }

    /**
     * Rechecks a degraded runtime database and restores service or performs confirmed recovery.
     */
    public synchronized boolean recheckAndRecover() {
        if (health.snapshot().state() != StorageState.DEGRADED) {
            return health.snapshot().available();
        }
        try {
            connectionManager.closeWriteConnection();
            connectionManager.open();
            recoveryManager.verifyQuickCheck();
            schemaMigrator.migrate();
            health.markAvailable("SQLite storage recovered after a successful health check");
            return true;
        } catch (SchemaVersionException failure) {
            health.markIncompatible(failure.getMessage());
            connectionManager.closeWriteConnection();
            return false;
        } catch (SchemaValidationException failure) {
            return recoverFromInvalidSchema(failure);
        } catch (RuntimeException failure) {
            if (recoveryManager.isConfirmedCorruption(failure)) {
                return recoverFromCorruption(failure);
            }
            health.markDegraded("SQLite storage health check failed: " + safeMessage(failure));
            return false;
        }
    }

    @Override
    public synchronized void close() {
        connectionManager.close();
        health.markClosed();
    }

    private boolean recoverFromCorruption(RuntimeException originalFailure) {
        try {
            recoveryManager.quarantineAndRecreate();
            health.markAvailable("SQLite storage recovered after isolating a corrupt database");
            return true;
        } catch (RuntimeException recoveryFailure) {
            recoveryFailure.addSuppressed(originalFailure);
            health.markDegraded("SQLite recovery failed: " + safeMessage(recoveryFailure));
            connectionManager.closeWriteConnection();
            return false;
        }
    }

    private boolean recoverFromInvalidSchema(RuntimeException originalFailure) {
        try {
            recoveryManager.quarantineInvalidSchemaAndRecreate();
            health.markAvailable("SQLite storage recovered after isolating an invalid schema");
            return true;
        } catch (RuntimeException recoveryFailure) {
            recoveryFailure.addSuppressed(originalFailure);
            health.markDegraded("SQLite schema recovery failed: " + safeMessage(recoveryFailure));
            connectionManager.closeWriteConnection();
            return false;
        }
    }

    private static String safeMessage(Throwable failure) {
        return FailureSummary.of(failure);
    }
}
