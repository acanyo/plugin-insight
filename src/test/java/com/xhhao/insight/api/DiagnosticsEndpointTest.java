package com.xhhao.insight.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xhhao.insight.diagnostics.DiagnosticsLifecycle;
import com.xhhao.insight.diagnostics.GcMemoryObserver;
import com.xhhao.insight.diagnostics.HaloLogService;
import com.xhhao.insight.diagnostics.JvmPressureWindow;
import com.xhhao.insight.diagnostics.ThreadDiagnostics;
import com.xhhao.insight.diagnostics.logging.PluginLogClassIndex;
import com.xhhao.insight.tasks.HaloTaskMonitor;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;

class DiagnosticsEndpointTest {
    @Test
    void forwardsImmutableSnapshotAndReportsExpiredQueriesAsConflict() throws Exception {
        var logs = mock(HaloLogService.class);
        when(logs.read(0, "all", "", 2, 20, "alpha", "expired", "page"))
                .thenThrow(
                        new com.xhhao.insight.diagnostics.DiagnosticConflictException(
                                "日志查询快照已过期，请重新查询"));
        var io = new InsightIoScheduler();
        try {
            var endpoint =
                    new DiagnosticsEndpoint(
                            new DiagnosticHttp(io),
                            mock(JvmPressureWindow.class),
                            mock(GcMemoryObserver.class),
                            mock(ThreadDiagnostics.class),
                            logs,
                            mock(HaloTaskMonitor.class),
                            mock(DiagnosticsLifecycle.class));
            WebTestClient.bindToRouterFunction(endpoint.endpoint())
                    .build()
                    .get()
                    .uri("/diagnostic-logs?pluginId=alpha&page=2&snapshotId=expired&mode=page")
                    .exchange()
                    .expectStatus()
                    .isEqualTo(409)
                    .expectBody()
                    .jsonPath("$.message")
                    .isEqualTo("日志查询快照已过期，请重新查询");
            verify(logs).read(0, "all", "", 2, 20, "alpha", "expired", "page");
        } finally {
            io.destroy();
        }
    }

    @Test
    void forwardsPluginQueryAndReturnsAttributionMetadata() throws Exception {
        var logs = mock(HaloLogService.class);
        when(logs.read(0, "ERROR", "failed", 2, 20, "alpha", "", "query"))
                .thenReturn(
                        new HaloLogService.Result(
                                true,
                                List.of("halo.log"),
                                List.of(
                                        new HaloLogService.Entry(
                                                "ERROR",
                                                "failed",
                                                "com.example.Worker",
                                                List.of("alpha"),
                                                "logger")),
                                List.of(),
                                false,
                                2,
                                20,
                                21,
                                false,
                                List.of(new PluginLogClassIndex.PluginOption("alpha", "甲插件", true)),
                                "alpha",
                                1,
                                "ready"));
        var io = new InsightIoScheduler();
        try {
            var endpoint =
                    new DiagnosticsEndpoint(
                            new DiagnosticHttp(io),
                            mock(JvmPressureWindow.class),
                            mock(GcMemoryObserver.class),
                            mock(ThreadDiagnostics.class),
                            logs,
                            mock(HaloTaskMonitor.class),
                            mock(DiagnosticsLifecycle.class));
            WebTestClient.bindToRouterFunction(endpoint.endpoint())
                    .build()
                    .get()
                    .uri(
                            "/diagnostic-logs?pluginId=alpha&level=ERROR&keyword=failed&page=2&pageSize=20")
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .jsonPath("$.pluginId")
                    .isEqualTo("alpha")
                    .jsonPath("$.entries[0].pluginIds[0]")
                    .isEqualTo("alpha")
                    .jsonPath("$.plugins[0].displayName")
                    .isEqualTo("甲插件");
            verify(logs).read(0, "ERROR", "failed", 2, 20, "alpha", "", "query");
        } finally {
            io.destroy();
        }
    }
}
