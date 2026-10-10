package com.xhhao.insight.api;

import com.xhhao.insight.diagnostics.*;
import com.xhhao.insight.tasks.HaloTaskMonitor;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;

@Component
public class DiagnosticsEndpoint implements CustomEndpoint {
    private final DiagnosticHttp http;
    private final JvmPressureWindow gc;
    private final GcMemoryObserver memory;
    private final ThreadDiagnostics threads;
    private final HaloLogService logs;
    private final HaloTaskMonitor tasks;
    private final DiagnosticsLifecycle lifecycle;

    public DiagnosticsEndpoint(DiagnosticHttp http, JvmPressureWindow gc, GcMemoryObserver memory,
        ThreadDiagnostics threads, HaloLogService logs, HaloTaskMonitor tasks, DiagnosticsLifecycle lifecycle) {
        this.http = http; this.gc = gc; this.memory = memory; this.threads = threads;
        this.logs = logs; this.tasks = tasks; this.lifecycle = lifecycle;
    }

    @Override public RouterFunction<ServerResponse> endpoint() {
        return RouterFunctions.route()
            .GET("diagnostics", request -> http.json(() -> {
                long now = System.currentTimeMillis();
                return new Summary(now, gc.windows(now), memory.snapshot(now), lifecycle.historyError());
            }))
            .GET("diagnostic-threads", request -> http.json(threads::snapshot))
            .GET("diagnostic-logs", request -> http.json(() -> logs.read(
                InsightApiInput.integerQuery(request, "source", 0), DiagnosticHttp.query(request, "level", "all"),
                DiagnosticHttp.query(request, "keyword", ""),
                InsightApiInput.integerQuery(request, "page", 1),
                InsightApiInput.integerQuery(request, "pageSize", 20),
                DiagnosticHttp.query(request, "pluginId", ""),
                DiagnosticHttp.query(request, "snapshotId", ""),
                DiagnosticHttp.query(request, "mode", "query"))))
            .GET("business-tasks", request -> http.json(tasks::snapshot))
            .GET("business-task-history", request -> http.json(tasks::history))
            .build();
    }
    @Override public GroupVersion groupVersion() { return GroupVersion.parseAPIVersion("console.api.insight.halo.run/v1alpha1"); }
    public record Summary(long atMs, List<JvmPressureWindow.GcWindow> gc,
                          GcMemoryObserver.MemoryTrend memory, String historyError) {}
}
