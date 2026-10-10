package com.xhhao.insight.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xhhao.insight.halo.PluginRuntimeInfo;
import com.xhhao.insight.overhead.InsightOverheadMetrics;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;

class PluginDiagnosticEndpointTest {
    @Test
    void returnsTargetedStructuredEvidenceAndForwardsBaselineSelection() {
        var queries = mock(PluginDiagnosticQueryService.class);
        var baselines = mock(PluginBaselineQueryService.class);
        when(queries.detail("alpha", "15m"))
                .thenReturn(
                        new PluginDiagnosticQueryService.Detail(
                                new PluginRuntimeInfo("alpha", "甲插件", "1.0", null, true, "STARTED", null),
                                "15m",
                                0,
                                900_000,
                                true,
                                null,
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of(),
                                "只覆盖归属线程"));
        when(baselines.compare("alpha", "15m", "upgrade"))
                .thenReturn(
                        new PluginBaselineQueryService.Comparison(
                                false, "upgrade", null, null, false, List.of("尚未积累完整窗口")));
        var io = new InsightIoScheduler();
        try {
            var endpoint = new PluginDiagnosticEndpoint(new DiagnosticHttp(io), queries, baselines);
            var client = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
            client.get()
                    .uri("/plugin-detail?pluginId=alpha&range=15m")
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .jsonPath("$.plugin.name")
                    .isEqualTo("alpha")
                    .jsonPath("$.coverage")
                    .isEqualTo("只覆盖归属线程")
                    .jsonPath("$.entries")
                    .doesNotExist();
            client.get()
                    .uri("/plugin-baseline?pluginId=alpha&range=15m&reference=upgrade")
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .jsonPath("$.available")
                    .isEqualTo(false)
                    .jsonPath("$.before")
                    .isEmpty()
                    .jsonPath("$.notices[0]")
                    .isEqualTo("尚未积累完整窗口");
            verify(queries).detail("alpha", "15m");
            verify(baselines).compare("alpha", "15m", "upgrade");
        } finally {
            io.destroy();
        }
    }

    @Test
    void rejectsInvalidPluginRequestsAndReturnsSelfOverheadWithoutReadingHistory() {
        var queries = mock(PluginDiagnosticQueryService.class);
        when(queries.detail("missing", "1h")).thenThrow(new IllegalArgumentException("插件不存在"));
        var io = new InsightIoScheduler();
        try {
            var http = new DiagnosticHttp(io);
            var endpoint =
                    new PluginDiagnosticEndpoint(
                            http, queries, mock(PluginBaselineQueryService.class));
            WebTestClient.bindToRouterFunction(endpoint.endpoint())
                    .build()
                    .get()
                    .uri("/plugin-detail?pluginId=missing")
                    .exchange()
                    .expectStatus()
                    .isBadRequest();
            var overhead = new InsightOverheadMetrics();
            var scope = overhead.begin(InsightOverheadMetrics.Operation.SQL_READ);
            scope.close();
            WebTestClient.bindToRouterFunction(new OverheadEndpoint(overhead, http).endpoint())
                    .build()
                    .get()
                    .uri("/monitor-overhead")
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .jsonPath("$.databaseReads")
                    .isEqualTo(1)
                    .jsonPath("$.cpuAvailable")
                    .isEqualTo(false);
        } finally {
            io.destroy();
        }
    }
}
