package com.xhhao.insight.api;

import com.xhhao.insight.optimization.OptimizationObservationService;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.*;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;

@Component
public class ObservationEndpoint implements CustomEndpoint {
    private final OptimizationObservationService observations;
    private final DiagnosticHttp http;
    public ObservationEndpoint(OptimizationObservationService observations, DiagnosticHttp http) {
        this.observations = observations; this.http = http;
    }
    @Override public RouterFunction<ServerResponse> endpoint() {
        return RouterFunctions.route()
            .GET("observations", request -> http.json(observations::list))
            .POST("observations", request -> http.json(() -> observations.prepare(DiagnosticHttp.query(request, "pluginId", ""))))
            .POST("observations/{id}/confirm", request -> http.json(() -> observations.confirm(DiagnosticHttp.id(request))))
            .POST("observations/{id}/cancel", request -> http.json(() -> observations.cancel(DiagnosticHttp.id(request))))
            .POST("observations/{id}/restored", request -> http.json(() -> observations.restored(DiagnosticHttp.id(request))))
            .build();
    }
    @Override public GroupVersion groupVersion() { return GroupVersion.parseAPIVersion("console.api.insight.halo.run/v1alpha1"); }
}
