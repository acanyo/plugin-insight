package com.xhhao.insight.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xhhao.insight.api.export.ExportBusyException;
import com.xhhao.insight.api.export.ExportCapacityException;
import com.xhhao.insight.api.export.ExportWorkspace;
import com.xhhao.insight.storage.StorageException;
import com.xhhao.insight.storage.database.SqliteConnectionManager;
import com.xhhao.insight.storage.database.SqliteStorageLifecycle;
import com.xhhao.insight.storage.model.LatencyHistogram;
import com.xhhao.insight.storage.model.RequestMetricMinute;
import com.xhhao.insight.storage.repository.RequestMetricRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MonitoringExportServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void publicConstructorDoesNotRequireAHostObjectMapperBean() {
        var connectionManager = org.mockito.Mockito.mock(SqliteConnectionManager.class);
        var workspace = org.mockito.Mockito.mock(ExportWorkspace.class);

        assertThat(new MonitoringExportService(connectionManager, workspace)).isNotNull();
    }

    @Test
    void streamsEveryMatchingRowWithoutRepositoryLimits() throws Exception {
        try (var storage = new SqliteStorageLifecycle(
            temporaryDirectory.resolve("insight.sqlite"))) {
            assertThat(storage.initialize()).isTrue();
            var requests = new RequestMetricRepository(storage.connectionManager());
            requests.saveBatch(java.util.List.of(new RequestMetricMinute(
                60_000, "halo-core", "/apis/posts", "GET", 2,
                3, 0, 30_000, 15_000,
                new LatencyHistogram(0, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0)
            )));
            var service = new MonitoringExportService(
                storage.connectionManager(), new ObjectMapper(),
                Clock.fixed(Instant.ofEpochMilli(120_000), ZoneOffset.UTC));

            var export = service.create("15m");
            try {
                var root = new ObjectMapper().readTree(export.path().toFile());
                assertThat(root.path("schemaVersion").asInt()).isEqualTo(3);
                assertThat(root.path("range").asText()).isEqualTo("15m");
                assertThat(root.path("consistency").asText()).isEqualTo("table-snapshot");
                assertThat(root.path("systemMetricMinutes").isArray()).isTrue();
                assertThat(root.path("pluginResourceMetrics").isArray()).isTrue();
                assertThat(root.path("resourceAttributionMetrics").isArray()).isTrue();
                assertThat(root.path("requestMetrics").size()).isEqualTo(1);
                assertThat(root.path("requestMetrics").get(0).path("route").asText())
                    .isEqualTo("/apis/posts");
                assertThat(export.sizeBytes()).isEqualTo(Files.size(export.path()));
            } finally {
                export.close();
            }
            assertThat(Files.exists(export.path())).isFalse();
        }
    }

    @Test
    void permitsOnlyOneExportUntilItsFileIsClosed() throws Exception {
        try (var storage = new SqliteStorageLifecycle(
            temporaryDirectory.resolve("insight.sqlite"))) {
            assertThat(storage.initialize()).isTrue();
            var service = new MonitoringExportService(
                storage.connectionManager(), new ObjectMapper(), fixedClock());

            var first = service.create("15m");
            try {
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.create("15m"))
                    .isInstanceOf(ExportBusyException.class)
                    .hasMessageContaining("already in progress");
            } finally {
                first.close();
            }

            try (var second = service.create("15m")) {
                assertThat(Files.exists(second.path())).isTrue();
            }
        }
    }

    @Test
    void removesPartialFileAndReleasesLeaseWhenGenerationFails() throws Exception {
        Path database = temporaryDirectory.resolve("insight.sqlite");
        var storage = new SqliteStorageLifecycle(database);
        assertThat(storage.initialize()).isTrue();
        var service = new MonitoringExportService(
            storage.connectionManager(), new ObjectMapper(), fixedClock());
        storage.close();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.create("15m"))
            .isInstanceOf(StorageException.class)
            .isNotInstanceOf(ExportBusyException.class);
        assertExportDirectoryEmpty(database);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.create("15m"))
            .isInstanceOf(StorageException.class)
            .isNotInstanceOf(ExportBusyException.class);
        assertExportDirectoryEmpty(database);
    }

    @Test
    void removesPartialFileWhenConfiguredMaximumIsExceeded() throws Exception {
        Path database = temporaryDirectory.resolve("insight.sqlite");
        try (var storage = new SqliteStorageLifecycle(database)) {
            assertThat(storage.initialize()).isTrue();
            Clock clock = fixedClock();
            var workspace = new ExportWorkspace(database, clock, 64);
            var service = new MonitoringExportService(
                storage.connectionManager(), new ObjectMapper(), workspace, clock);

            org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.create("15m"))
                .isInstanceOf(ExportCapacityException.class)
                .hasMessageContaining("maximum size");
            assertExportDirectoryEmpty(database);
        }
    }

    private static Clock fixedClock() {
        return Clock.fixed(Instant.ofEpochMilli(120_000), ZoneOffset.UTC);
    }

    private static void assertExportDirectoryEmpty(Path database) throws Exception {
        Path exports = database.toAbsolutePath().getParent().resolve("exports");
        if (!Files.exists(exports)) {
            return;
        }
        try (Stream<Path> files = Files.list(exports)) {
            assertThat(files).isEmpty();
        }
    }
}
