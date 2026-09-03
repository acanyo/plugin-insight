package com.xhhao.insight.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xhhao.insight.api.model.ClearDataResponse;
import com.xhhao.insight.api.model.OverviewResponse;
import com.xhhao.insight.notification.NotificationResult;
import com.xhhao.insight.storage.database.SqliteStorageLifecycle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserter;
import reactor.core.publisher.Flux;

class InsightEndpointTest {

    @TempDir
    Path temporaryDirectory;

    private final InsightIoScheduler scheduler = new InsightIoScheduler();

    @AfterEach
    void disposeScheduler() {
        scheduler.destroy();
    }

    @Test
    void shouldExposeOverviewAtCustomEndpointRelativePath() {
        OverviewQueryService service = mock(OverviewQueryService.class);
        MonitoringExportService exportService = mock(MonitoringExportService.class);
        when(service.overview()).thenReturn(overview());
        InsightEndpoint endpoint = endpoint(service, exportService);
        WebTestClient client = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();

        client.get().uri("/overview").exchange()
            .expectStatus().isOk()
            .expectHeader().contentType("application/json")
            .expectBody()
            .jsonPath("$.health").isEqualTo("healthy")
            .jsonPath("$.runtime.haloVersion").isEqualTo("2.26.0");
    }

    @Test
    void shouldExposeManagementCommandsAtRbacCompatibleCollectionRoutes() {
        NotificationCommandService notifications = mock(NotificationCommandService.class);
        MonitoringDataCommandService data = mock(MonitoringDataCommandService.class);
        when(notifications.testNotifications()).thenReturn(
            CompletableFuture.completedFuture(List.of(NotificationResult.sent("halo"))));
        when(data.clearData("events")).thenReturn(new ClearDataResponse("events", 4));
        InsightEndpoint endpoint = new InsightEndpoint(
            mock(OverviewQueryService.class), mock(PerformanceQueryService.class),
            mock(IncidentQueryService.class), mock(AlertCommandService.class),
            notifications, data, mock(MonitoringExportService.class), scheduler);
        WebTestClient client = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();

        client.post().uri("/notifications").exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$[0].channel").isEqualTo("halo")
            .jsonPath("$[0].sent").isEqualTo(true);
        client.post().uri("/notifications/test").exchange()
            .expectStatus().isNotFound();
        client.delete().uri("/data?scope=events").exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.scope").isEqualTo("events")
            .jsonPath("$.deletedRows").isEqualTo(4);
    }

    @Test
    void shouldStreamAndDeleteExportFileAfterResponseCompletes() throws Exception {
        Path database = temporaryDirectory.resolve("insight.sqlite");
        try (var storage = new SqliteStorageLifecycle(database)) {
            org.assertj.core.api.Assertions.assertThat(storage.initialize()).isTrue();
            var exportService = new MonitoringExportService(
                storage.connectionManager(), new ObjectMapper(), fixedClock());
            InsightEndpoint endpoint = endpoint(mock(OverviewQueryService.class), exportService);
            WebTestClient client = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();

            client.get().uri("/export?range=15m").exchange()
                .expectStatus().isOk()
                .expectHeader().valueMatches(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\\\"halo-insight-15m-.*\\.json\\\"")
                .expectBody()
                .jsonPath("$.schemaVersion").isEqualTo(3)
                .jsonPath("$.consistency").isEqualTo("table-snapshot")
                .jsonPath("$.systemMetricMinutes").isArray();

            assertExportDirectoryEmpty(database);
        }
    }

    @Test
    void shouldReturnTooManyRequestsWhenAnotherExportOwnsTheLease() throws Exception {
        Path database = temporaryDirectory.resolve("insight.sqlite");
        try (var storage = new SqliteStorageLifecycle(database)) {
            org.assertj.core.api.Assertions.assertThat(storage.initialize()).isTrue();
            var exportService = new MonitoringExportService(
                storage.connectionManager(), new ObjectMapper(), fixedClock());
            InsightEndpoint endpoint = endpoint(mock(OverviewQueryService.class), exportService);
            WebTestClient client = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();

            try (var first = exportService.create("15m")) {
                client.get().uri("/export?range=15m").exchange()
                    .expectStatus().isEqualTo(429)
                    .expectHeader().doesNotExist(HttpHeaders.CONTENT_DISPOSITION)
                    .expectBody()
                    .jsonPath("$.error").isEqualTo("export_busy")
                    .jsonPath("$.message").value(value ->
                        org.assertj.core.api.Assertions.assertThat(value.toString())
                            .isEqualTo("监控数据导出任务正在执行，请稍后重试"));
            }
        }
    }

    @Test
    void shouldRejectInvalidExportRangeBeforeWritingResponseBody() {
        OverviewQueryService service = mock(OverviewQueryService.class);
        MonitoringExportService exportService = mock(MonitoringExportService.class);
        doThrow(new IllegalArgumentException("Unsupported range"))
            .when(exportService).validateRange("forever");
        InsightEndpoint endpoint = endpoint(service, exportService);
        WebTestClient client = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();

        client.get().uri("/export?range=forever").exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.error").isEqualTo("bad_request");
    }

    @Test
    void shouldDeleteExportFileAndReleaseLeaseWhenBodyIsCancelled() throws Exception {
        Path database = temporaryDirectory.resolve("insight.sqlite");
        try (var storage = new SqliteStorageLifecycle(database)) {
            org.assertj.core.api.Assertions.assertThat(storage.initialize()).isTrue();
            var exportService = new MonitoringExportService(
                storage.connectionManager(), new ObjectMapper(), fixedClock());
            CountDownLatch readStarted = new CountDownLatch(1);
            AtomicReference<Path> exportedPath = new AtomicReference<>();
            InsightEndpoint.ExportContentReader suspendedReader = (path, ignored) -> {
                exportedPath.set(path);
                readStarted.countDown();
                return Flux.never();
            };
            InsightEndpoint endpoint = new InsightEndpoint(
                mock(OverviewQueryService.class), mock(PerformanceQueryService.class),
                mock(IncidentQueryService.class), mock(AlertCommandService.class),
                mock(NotificationCommandService.class), mock(MonitoringDataCommandService.class),
                exportService, scheduler, suspendedReader);
            MockServerHttpResponse response = new MockServerHttpResponse();
            BodyInserter.Context context = mock(BodyInserter.Context.class);

            var subscription = endpoint.exportBody("15m").insert(response, context)
                .subscribe();
            try {
                org.assertj.core.api.Assertions.assertThat(
                    readStarted.await(2, TimeUnit.SECONDS)).isTrue();
                org.assertj.core.api.Assertions.assertThat(exportedPath.get()).exists();
            } finally {
                subscription.dispose();
            }

            awaitDeletion(exportedPath.get());
            try (var retry = exportService.create("15m")) {
                org.assertj.core.api.Assertions.assertThat(retry.path()).exists();
            }
        }
    }

    private static Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-09-02T00:00:00Z"), ZoneOffset.UTC);
    }

    private InsightEndpoint endpoint(
        OverviewQueryService overview,
        MonitoringExportService exportService
    ) {
        return new InsightEndpoint(
            overview, mock(PerformanceQueryService.class), mock(IncidentQueryService.class),
            mock(AlertCommandService.class), mock(NotificationCommandService.class),
            mock(MonitoringDataCommandService.class), exportService, scheduler);
    }

    private static void assertExportDirectoryEmpty(Path database) throws Exception {
        Path directory = database.toAbsolutePath().getParent().resolve("exports");
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> files = Files.list(directory)) {
            org.assertj.core.api.Assertions.assertThat(files).isEmpty();
        }
    }

    private static void awaitDeletion(Path path) throws Exception {
        for (int attempt = 0; attempt < 100 && Files.exists(path); attempt++) {
            Thread.sleep(10);
        }
        org.assertj.core.api.Assertions.assertThat(path).doesNotExist();
    }

    private static OverviewResponse overview() {
        return new OverviewResponse(
            1, "healthy",
            new OverviewResponse.RuntimeView(true, 1, "2.26.0", "21", "macOS",
                0, 0, 0, 0, 0, 0, 1, null),
            new OverviewResponse.JvmView(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            new OverviewResponse.RequestView(0, 1, 0, 0, 0, 0, 0, 0, 0, Map.of()),
            new OverviewResponse.StorageView("available", "ok", true, false,
                0, 1, 1, 0),
            List.of(), List.of(), List.of()
        );
    }
}
