package com.xhhao.insight.api;

import com.xhhao.insight.overhead.InsightOverheadMetrics;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;

@Component
public class OverheadEndpoint implements CustomEndpoint {
    private final InsightOverheadMetrics overhead;
    private final DiagnosticHttp http;

    public OverheadEndpoint(InsightOverheadMetrics overhead, DiagnosticHttp http) {
        this.overhead = overhead;
        this.http = http;
    }

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return RouterFunctions.route()
                .GET("monitor-overhead", request -> http.json(overhead::snapshot))
                .build();
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("console.api.insight.halo.run/v1alpha1");
    }
}
