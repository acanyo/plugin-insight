package com.xhhao.insight.api;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;

@Component
public class PluginDiagnosticEndpoint implements CustomEndpoint {
    private final DiagnosticHttp http;
    private final PluginDiagnosticQueryService queries;
    private final PluginBaselineQueryService baselines;

    public PluginDiagnosticEndpoint(
            DiagnosticHttp http,
            PluginDiagnosticQueryService queries,
            PluginBaselineQueryService baselines) {
        this.http = http;
        this.queries = queries;
        this.baselines = baselines;
    }

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return RouterFunctions.route()
                .GET(
                        "plugin-detail",
                        request ->
                                http.json(
                                        () ->
                                                queries.detail(
                                                        DiagnosticHttp.query(
                                                                request, "pluginId", ""),
                                                        DiagnosticHttp.query(
                                                                request, "range", "1h"))))
                .GET(
                        "plugin-baseline",
                        request ->
                                http.json(
                                        () ->
                                                baselines.compare(
                                                        DiagnosticHttp.query(
                                                                request, "pluginId", ""),
                                                        DiagnosticHttp.query(
                                                                request, "range", "1h"),
                                                        DiagnosticHttp.query(
                                                                request, "reference", "previous"))))
                .build();
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("console.api.insight.halo.run/v1alpha1");
    }
}
