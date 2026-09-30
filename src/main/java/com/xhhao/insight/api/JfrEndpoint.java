package com.xhhao.insight.api;

import com.xhhao.insight.diagnostics.JfrDiagnostics;
import java.util.Map;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;

@Component
public class JfrEndpoint implements CustomEndpoint {
    private final JfrDiagnostics jfr;
    private final DiagnosticHttp http;
    private final InsightIoScheduler io;
    public JfrEndpoint(JfrDiagnostics jfr, DiagnosticHttp http, InsightIoScheduler io) {
        this.jfr = jfr; this.http = http; this.io = io;
    }
    @Override public RouterFunction<ServerResponse> endpoint() {
        return RouterFunctions.route()
            .GET("recordings", request -> http.json(() -> Map.of("available", jfr.available(), "sessions", jfr.list())))
            .POST("recordings", request -> http.json(() -> jfr.start(InsightApiInput.integerQuery(request, "seconds", 30))))
            .GET("recordings/{id}/download", request -> Mono.fromCallable(() -> jfr.download(DiagnosticHttp.id(request)))
                .subscribeOn(io.scheduler()).flatMap(path -> ServerResponse.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header("Content-Disposition", "attachment; filename=\"" + path.getFileName() + "\"")
                    .bodyValue(new FileSystemResource(path))).onErrorResume(DiagnosticHttp::error))
            .build();
    }
    @Override public GroupVersion groupVersion() { return GroupVersion.parseAPIVersion("console.api.insight.halo.run/v1alpha1"); }
}
