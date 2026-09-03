package com.xhhao.insight.api.export;

import com.xhhao.insight.storage.StorageException;
import com.xhhao.insight.storage.database.SqliteConnectionManager;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Owns the bounded, single-writer workspace used by monitoring exports.
 */
@Slf4j
@Component
public class ExportWorkspace {

    static final String FILE_PREFIX = "halo-insight-export-";
    static final String FILE_SUFFIX = ".json";
    public static final long DEFAULT_MAXIMUM_BYTES = 512L * 1_024 * 1_024;
    static final long MINIMUM_FREE_BYTES = 64L * 1_024 * 1_024;
    private static final Duration STALE_AFTER = Duration.ofHours(24);

    private final Path directory;
    private final Clock clock;
    private final long maximumBytes;
    private final AtomicBoolean leased = new AtomicBoolean();

    @Autowired
    public ExportWorkspace(SqliteConnectionManager connectionManager) {
        this(connectionManager.databasePath(), Clock.systemUTC(), DEFAULT_MAXIMUM_BYTES);
    }

    public ExportWorkspace(Path databasePath, Clock clock, long maximumBytes) {
        Path parent = Objects.requireNonNull(databasePath, "databasePath must not be null")
            .toAbsolutePath().normalize().getParent();
        if (parent == null) {
            throw new IllegalArgumentException("databasePath must have a parent directory");
        }
        if (maximumBytes <= 0) {
            throw new IllegalArgumentException("maximumBytes must be positive");
        }
        this.directory = parent.resolve("exports");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.maximumBytes = maximumBytes;
    }

    @PostConstruct
    void cleanOnStartup() {
        try {
            prepareDirectory();
            cleanExpiredFiles();
        } catch (RuntimeException error) {
            log.warn("Failed to clean the Insight export workspace on startup", error);
        }
    }

    public Lease acquire() {
        if (!leased.compareAndSet(false, true)) {
            throw new ExportBusyException();
        }
        try {
            prepareDirectory();
            cleanExpiredFiles();
            ensureUsableSpace();
            Path path = Files.createTempFile(directory, FILE_PREFIX, FILE_SUFFIX);
            return new Lease(path);
        } catch (IOException | RuntimeException error) {
            leased.set(false);
            if (error instanceof StorageException storageError) {
                throw storageError;
            }
            throw new StorageException("Failed to prepare the Insight export workspace", error);
        }
    }

    Path directory() {
        return directory;
    }

    private void prepareDirectory() {
        try {
            Files.createDirectories(directory);
        } catch (IOException error) {
            throw new StorageException("Failed to create the Insight export directory", error);
        }
    }

    private void ensureUsableSpace() {
        try {
            long requiredBytes = Math.addExact(maximumBytes, MINIMUM_FREE_BYTES);
            long usableBytes = Files.getFileStore(directory).getUsableSpace();
            if (usableBytes < requiredBytes) {
                throw new ExportCapacityException(
                    "Insufficient disk space for an Insight export: " + usableBytes
                        + " bytes usable, " + requiredBytes + " bytes required");
            }
        } catch (ArithmeticException error) {
            throw new ExportCapacityException("Insight export size limit is too large", error);
        } catch (IOException error) {
            throw new ExportCapacityException(
                "Failed to inspect disk space for the Insight export", error);
        }
    }

    private void cleanExpiredFiles() {
        long cutoffMs = clock.millis() - STALE_AFTER.toMillis();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(
            directory, FILE_PREFIX + "*" + FILE_SUFFIX)) {
            for (Path file : files) {
                cleanExpiredFile(file, cutoffMs);
            }
        } catch (IOException error) {
            log.warn("Failed to scan the Insight export workspace {}", directory, error);
        }
    }

    private void cleanExpiredFile(Path file, long cutoffMs) {
        try {
            if (Files.isRegularFile(file)
                && Files.getLastModifiedTime(file).toMillis() < cutoffMs) {
                Files.deleteIfExists(file);
            }
        } catch (IOException error) {
            log.warn("Failed to delete stale Insight export {}", file, error);
        }
    }

    private void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException error) {
            log.warn("Failed to delete Insight export {}", path, error);
        }
    }

    public final class Lease implements AutoCloseable {

        private final Path path;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicBoolean outputOpened = new AtomicBoolean();

        private Lease(Path path) {
            this.path = path;
        }

        public Path path() {
            return path;
        }

        public OutputStream openOutput() throws IOException {
            if (closed.get()) {
                throw new IllegalStateException("Export workspace lease is closed");
            }
            if (!outputOpened.compareAndSet(false, true)) {
                throw new IllegalStateException("Export workspace output was already opened");
            }
            return new SizeLimitedOutputStream(Files.newOutputStream(path,
                StandardOpenOption.TRUNCATE_EXISTING), maximumBytes);
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            try {
                delete(path);
            } finally {
                leased.set(false);
            }
        }
    }
}
