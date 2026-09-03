package com.xhhao.insight.storage.database;

import com.xhhao.insight.storage.StorageException;
import com.xhhao.insight.storage.health.StorageHealth;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import run.halo.app.plugin.PluginsRootGetter;

@Component
public class SqliteConnectionManager implements AutoCloseable {

    public static final int BUSY_TIMEOUT_MS = 5_000;
    private static final int MAX_BUSY_ATTEMPTS = 3;

    private final Path databasePath;
    private final String jdbcUrl;
    private final StorageHealth health;
    private final SqliteDriverLease driverLease = new SqliteDriverLease();
    private final ReentrantLock writeLock = new ReentrantLock();

    private volatile Connection writeConnection;
    private volatile boolean closed;

    @Autowired
    public SqliteConnectionManager(PluginsRootGetter pluginsRootGetter, StorageHealth health) {
        this(resolveDatabasePath(pluginsRootGetter), health);
    }

    public SqliteConnectionManager(Path databasePath) {
        this(databasePath, new StorageHealth());
    }

    public SqliteConnectionManager(Path databasePath, StorageHealth health) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath must not be null")
            .toAbsolutePath()
            .normalize();
        this.jdbcUrl = "jdbc:sqlite:" + this.databasePath;
        this.health = Objects.requireNonNull(health, "health must not be null");
    }

    private static Path resolveDatabasePath(PluginsRootGetter pluginsRootGetter) {
        Objects.requireNonNull(pluginsRootGetter, "pluginsRootGetter must not be null");
        return pluginsRootGetter.get().resolve("insight").resolve("insight.sqlite");
    }

    public synchronized void open() {
        if (isWriteConnectionOpen()) {
            return;
        }
        closed = false;
        try {
            Files.createDirectories(databasePath.getParent());
            driverLease.acquire();
            Connection connection = DriverManager.getConnection(jdbcUrl);
            configureWriteConnection(connection);
            writeConnection = connection;
        } catch (IOException | SQLException | RuntimeException e) {
            closeWriteConnectionQuietly();
            driverLease.release();
            throw new StorageException("Failed to open SQLite database at " + databasePath, e);
        }
    }

    public <T> T write(SqlWork<T> work) {
        Objects.requireNonNull(work, "work must not be null");
        writeLock.lock();
        try {
            Connection connection = requireWriteConnection();
            SQLException lastBusyFailure = null;
            for (int attempt = 1; attempt <= MAX_BUSY_ATTEMPTS; attempt++) {
                try {
                    return executeTransaction(connection, work);
                } catch (SQLException e) {
                    if (!isBusy(e) || attempt == MAX_BUSY_ATTEMPTS) {
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
        }
    }

    public <T> T read(SqlWork<T> work) {
        Objects.requireNonNull(work, "work must not be null");
        requireWriteConnection();
        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            configureReadConnection(connection);
            return work.execute(connection);
        } catch (SQLException e) {
            if (isCorruption(e)) {
                health.markDegraded("SQLite reported database corruption");
            } else {
                health.markDegraded("SQLite read failed");
            }
            throw new StorageException("SQLite read failed", e);
        }
    }

    /**
     * Executes multiple read statements against one stable WAL snapshot.
     */
    public <T> T readTransaction(SqlWork<T> work) {
        Objects.requireNonNull(work, "work must not be null");
        requireWriteConnection();
        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            configureReadConnection(connection);
            connection.setAutoCommit(false);
            try {
                T result = work.execute(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException error) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    error.addSuppressed(rollbackFailure);
                }
                throw error;
            }
        } catch (SQLException error) {
            if (isCorruption(error)) {
                health.markDegraded("SQLite reported database corruption");
            } else {
                health.markDegraded("SQLite snapshot read failed");
            }
            throw new StorageException("SQLite snapshot read failed", error);
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
            statement.execute("PRAGMA synchronous=NORMAL");
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
        writeLock.lock();
        try {
            closeWriteConnectionQuietly();
        } finally {
            writeLock.unlock();
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
            if (current.getErrorCode() == 5 || current.getErrorCode() == 6) {
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
            throw new StorageException("Interrupted while waiting to retry SQLite write", e);
        }
    }
}
