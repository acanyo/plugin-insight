package com.xhhao.insight.storage.database;

import com.xhhao.insight.storage.StorageException;
import com.xhhao.insight.storage.health.StorageHealth;
import com.xhhao.insight.overhead.InsightOverheadMetrics;
import com.xhhao.insight.overhead.InsightOverheadMetrics.Operation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import run.halo.app.plugin.PluginsRootGetter;

@Slf4j
@Component
public class SqliteConnectionManager implements AutoCloseable {

    public static final int BUSY_TIMEOUT_MS = 5_000;
    private static final int MAX_BUSY_ATTEMPTS = 3;
    private static final int MAX_READ_ATTEMPTS = 2;

    private final Path databasePath;
    private final String jdbcUrl;
    private final StorageHealth health;
    private final InsightOverheadMetrics overhead;
    private final SqliteDriverLease driverLease = new SqliteDriverLease();
    private final ReentrantLock writeLock = new ReentrantLock();
    private final ReentrantReadWriteLock lifecycleLock = new ReentrantReadWriteLock(true);

    private volatile Connection writeConnection;
    private volatile boolean closed;

    @Autowired
    public SqliteConnectionManager(PluginsRootGetter pluginsRootGetter, StorageHealth health, InsightOverheadMetrics overhead) {
        this(resolveDatabasePath(pluginsRootGetter), health, overhead);
    }

    public SqliteConnectionManager(Path databasePath) {
        this(databasePath, new StorageHealth());
    }

    public SqliteConnectionManager(Path databasePath, StorageHealth health) {
        this(databasePath, health, null);
    }

    public SqliteConnectionManager(Path databasePath, StorageHealth health, InsightOverheadMetrics overhead) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath must not be null")
            .toAbsolutePath()
            .normalize();
        this.jdbcUrl = "jdbc:sqlite:" + this.databasePath;
        this.health = Objects.requireNonNull(health, "health must not be null");
        this.overhead = overhead;
    }

    private static Path resolveDatabasePath(PluginsRootGetter pluginsRootGetter) {
        Objects.requireNonNull(pluginsRootGetter, "pluginsRootGetter must not be null");
        return pluginsRootGetter.get().resolve("insight").resolve("insight.sqlite");
    }

    public synchronized void open() {
        lifecycleLock.writeLock().lock();
        Connection opening = null;
        try {
            if (isWriteConnectionOpen()) {
                return;
            }
            closed = false;
            Files.createDirectories(databasePath.getParent());
            driverLease.acquire();
            opening = DriverManager.getConnection(jdbcUrl);
            configureWriteConnection(opening);
            writeConnection = opening;
        } catch (IOException | SQLException | RuntimeException e) {
            if (opening != null) {
                try { opening.close(); } catch (SQLException closeError) { e.addSuppressed(closeError); }
            }
            closeWriteConnectionQuietly();
            driverLease.release();
            throw new StorageException("Failed to open SQLite database at " + databasePath, e);
        } finally {
            lifecycleLock.writeLock().unlock();
        }
    }

    public <T> T write(SqlWork<T> work) {
        try (var scope = overhead == null ? null : overhead.begin(Operation.SQL_WRITE)) {
            try { return executeWrite(work); }
            catch (RuntimeException | Error failure) { if (scope != null) scope.failed(); throw failure; }
        }
    }

    private <T> T executeWrite(SqlWork<T> work) {
        Objects.requireNonNull(work, "work must not be null");
        lifecycleLock.readLock().lock();
        writeLock.lock();
        try {
            Connection connection = requireWriteConnection();
            SQLException lastBusyFailure = null;
            for (int attempt = 1; attempt <= MAX_BUSY_ATTEMPTS; attempt++) {
                try {
                    return executeTransaction(connection, work);
                } catch (SQLException e) {
                    if (!isBusy(e) || attempt == MAX_BUSY_ATTEMPTS) {
                        reportFailure("write", e);
                        if (isCorruption(e)) {
                            health.markDegraded("SQLite reported database corruption");
                        } else {
                            health.markDegraded("SQLite write transaction failed");
                        }
                        throw new StorageException("SQLite write transaction failed", e);
                    }
                    lastBusyFailure = e;
                    pauseBeforeRetry(attempt);
                }
            }
            throw new StorageException("SQLite remained busy after retries", lastBusyFailure);
        } finally {
            writeLock.unlock();
            lifecycleLock.readLock().unlock();
        }
    }

    /** Read callbacks must be replay-safe: a transient BUSY/LOCKED may retry once. */
    public <T> T read(SqlWork<T> work) {
        return executeRead(work, false);
    }

    /**
     * Executes against one stable WAL snapshot. Never replays a started callback,
     * which may have already streamed data to an export file.
     */
    public <T> T readTransaction(SqlWork<T> work) {
        return executeRead(work, true);
    }

    private <T> T executeRead(SqlWork<T> work, boolean transaction) {
        try (var scope = overhead == null ? null : overhead.begin(Operation.SQL_READ)) {
            try { return executeReadMeasured(work, transaction); }
            catch (RuntimeException | Error failure) { if (scope != null) scope.failed(); throw failure; }
        }
    }

    private <T> T executeReadMeasured(SqlWork<T> work, boolean transaction) {
        Objects.requireNonNull(work, "work must not be null");
        lifecycleLock.readLock().lock();
        try {
            requireWriteConnection();
            for (int attempt = 1; attempt <= MAX_READ_ATTEMPTS; attempt++) {
                boolean callbackStarted = false;
                try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
                    configureReadConnection(connection);
                    callbackStarted = true;
                    return transaction ? executeTransaction(connection, work) : work.execute(connection);
                } catch (SQLException error) {
                    if (isBusy(error) && attempt < MAX_READ_ATTEMPTS && (!transaction || !callbackStarted)) {
                        pauseBeforeRetry(attempt);
                        continue;
                    }
                    String operation = transaction ? "snapshot read" : "read";
                    reportFailure(operation, error);
                    health.markDegraded(isCorruption(error) ? "SQLite reported database corruption"
                        : "SQLite " + operation + " failed (code=" + error.getErrorCode() + ")");
                    throw new StorageException("SQLite " + operation + " failed", error);
                }
            }
            throw new IllegalStateException("SQLite read attempts exhausted");
        } finally {
            lifecycleLock.readLock().unlock();
        }
    }

    private static <T> T executeTransaction(Connection connection, SqlWork<T> work)
        throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            T result = work.execute(connection);
            connection.commit();
            return result;
        } catch (SQLException | RuntimeException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                e.addSuppressed(rollbackFailure);
            }
            throw e;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private static void configureWriteConnection(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout=" + BUSY_TIMEOUT_MS);
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("PRAGMA wal_autocheckpoint=1000");
            statement.execute("PRAGMA temp_store=MEMORY");
        }
    }

    private static void configureReadConnection(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout=" + BUSY_TIMEOUT_MS);
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA query_only=ON");
        }
    }

    public Path databasePath() {
        return databasePath;
    }

    public StorageHealth health() {
        return health;
    }

    public boolean isOpen() {
        return isWriteConnectionOpen();
    }

    public void closeWriteConnection() {
        lifecycleLock.writeLock().lock();
        try {
            closeWriteConnectionQuietly();
        } finally {
            lifecycleLock.writeLock().unlock();
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closeWriteConnection();
        driverLease.release();
        closed = true;
    }

    private Connection requireWriteConnection() {
        Connection connection = writeConnection;
        try {
            if (connection == null || connection.isClosed()) {
                if (health.snapshot().available()) {
                    health.markDegraded("SQLite connection is not open");
                }
                throw new StorageException("SQLite storage is not open");
            }
            return connection;
        } catch (SQLException e) {
            if (health.snapshot().available()) {
                health.markDegraded("Failed to inspect SQLite connection");
            }
            throw new StorageException("Failed to inspect SQLite connection", e);
        }
    }

    private boolean isWriteConnectionOpen() {
        Connection connection = writeConnection;
        if (connection == null) {
            return false;
        }
        try {
            return !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    private void closeWriteConnectionQuietly() {
        Connection connection = writeConnection;
        writeConnection = null;
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ignored) {
            // Health has already been reported by the caller, if needed.
        }
    }

    private static boolean isBusy(SQLException exception) {
        SQLException current = exception;
        while (current != null) {
            int primaryCode = current.getErrorCode() & 0xff;
            if (primaryCode == 5 || primaryCode == 6) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && (message.contains("SQLITE_BUSY")
                || message.contains("SQLITE_LOCKED"))) {
                return true;
            }
            current = current.getNextException();
        }
        return false;
    }

    private static void reportFailure(String operation, SQLException error) {
        // Error codes and the call site aid diagnosis without logging SQL or stored payloads.
        var site = java.util.Arrays.stream(error.getStackTrace())
            .filter(frame -> frame.getClassName().startsWith("com.xhhao.insight."))
            .findFirst().map(Object::toString).orElse("unknown");
        log.warn("Insight SQLite {} failed: code={}, state={}, site={}",
            operation, error.getErrorCode(), error.getSQLState(), site);
    }

    public static boolean isCorruption(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException sqlException
                && (sqlException.getErrorCode() == 11 || sqlException.getErrorCode() == 26)) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && (message.contains("SQLITE_CORRUPT")
                || message.contains("SQLITE_NOTADB")
                || message.contains("database disk image is malformed")
                || message.contains("file is not a database"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static void pauseBeforeRetry(int attempt) {
        try {
            Thread.sleep(25L * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StorageException("Interrupted while waiting to retry SQLite operation", e);
        }
    }
}
