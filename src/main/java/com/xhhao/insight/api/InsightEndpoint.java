package com.xhhao.insight.api;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ReactiveHttpOutputMessage;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;

@Component
@Slf4j
public class InsightEndpoint implements CustomEndpoint {

    private final OverviewQueryService overviewQueries;
    private final PerformanceQueryService performanceQueries;
    private final IncidentQueryService incidentQueries;
    private final AlertCommandService alertCommands;
    private final NotificationCommandService notificationCommands;
    private final MonitoringDataCommandService dataCommands;
    private final MonitoringExportService exportService;
    private final InsightIoScheduler ioScheduler;
    private final ExportContentReader exportContentReader;

    @Autowired
    public InsightEndpoint(
        OverviewQueryService overviewQueries,
        PerformanceQueryService performanceQueries,
        IncidentQueryService incidentQueries,
        AlertCommandService alertCommands,
        NotificationCommandService notificationCommands,
        MonitoringDataCommandService dataCommands,
        MonitoringExportService exportService,
        InsightIoScheduler ioScheduler
    ) {
        this(overviewQueries, performanceQueries, incidentQueries, alertCommands,
            notificationCommands, dataCommands, exportService, ioScheduler,
            (path, bufferFactory) -> DataBufferUtils.read(path, bufferFactory, 64 * 1_024));
    }

    InsightEndpoint(
        OverviewQueryService overviewQueries,
        PerformanceQueryService performanceQueries,
        IncidentQueryService incidentQueries,
        AlertCommandService alertCommands,
        NotificationCommandService notificationCommands,
        MonitoringDataCommandService dataCommands,
        MonitoringExportService exportService,
        InsightIoScheduler ioScheduler,
        ExportContentReader exportContentReader
    ) {
        this.overviewQueries = overviewQueries;
        this.performanceQueries = performanceQueries;
        this.incidentQueries = incidentQueries;
        this.alertCommands = alertCommands;
        this.notificationCommands = notificationCommands;
        this.dataCommands = dataCommands;
        this.exportService = exportService;
        this.ioScheduler = ioScheduler;
        this.exportContentReader = exportContentReader;
    }

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return RouterFunctions.route()
            .GET("overview", this::overview)
            .GET("trends", this::trends)
            .GET("plugins", this::plugins)
            .GET("routes", this::routes)
            .GET("events", this::events)
            .GET("alerts", this::alerts)
            .GET("alerts/history", this::alertHistory)
            .POST("alerts/{id}/acknowledge", this::acknowledge)
            .POST("notifications", this::testNotifications)
            .DELETE("data", this::clearData)
            .GET("export", this::export)
            .build();
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("console.api.insight.halo.run/v1alpha1");
    }

    private Mono<ServerResponse> overview(ServerRequest request) {
        return json(overviewQueries::overview);
    }

    private Mono<ServerResponse> trends(ServerRequest request) {
        return json(() -> performanceQueries.trends(query(request, "range", "1h")));
    }

    private Mono<ServerResponse> plugins(ServerRequest request) {
        return json(() -> performanceQueries.plugins(query(request, "range", "1h"),
            query(request, "sort", "load")));
    }

    private Mono<ServerResponse> routes(ServerRequest request) {
        return json(() -> performanceQueries.routes(query(request, "range", "1h"),
            query(request, "sort", "p95")));
    }

    private Mono<ServerResponse> events(ServerRequest request) {
        return json(() -> incidentQueries.events(
            InsightApiInput.optionalQuery(
                request, "cursor", InsightApiInput.CURSOR_MAX_LENGTH),
            integerQuery(request, "limit", 50),
            InsightApiInput.optionalQuery(
                request, "type", InsightApiInput.FILTER_MAX_LENGTH),
            InsightApiInput.optionalQuery(
                request, "severity", InsightApiInput.ENUM_MAX_LENGTH)
        ));
    }

    private Mono<ServerResponse> alerts(ServerRequest request) {
        return json(() -> incidentQueries.alerts(query(request, "status", "all"),
            integerQuery(request, "limit", 100)));
    }

    private Mono<ServerResponse> alertHistory(ServerRequest request) {
        return json(() -> incidentQueries.alertHistory(
            query(request, "status", "all"),
            InsightApiInput.optionalQuery(
                request, "cursor", InsightApiInput.CURSOR_MAX_LENGTH),
            integerQuery(request, "limit", 50)));
    }

    private Mono<ServerResponse> acknowledge(ServerRequest request) {
        return json(() -> alertCommands.acknowledge(InsightApiInput.pathVariable(
            request, "id", InsightApiInput.RESOURCE_ID_MAX_LENGTH)));
    }

    private Mono<ServerResponse> testNotifications(ServerRequest request) {
        return Mono.defer(() -> Mono.fromFuture(notificationCommands.testNotifications()))
            .flatMap(result -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
                .bodyValue(result))
            .onErrorResume(this::errorResponse);
    }

    private Mono<ServerResponse> clearData(ServerRequest request) {
        return json(() -> dataCommands.clearData(query(request, "scope", "all")));
    }

    private Mono<ServerResponse> export(ServerRequest request) {
        String rangeName = query(request, "range", "24h");
        try {
            exportService.validateRange(rangeName);
        } catch (RuntimeException error) {
            return errorResponse(error);
        }
        return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(exportBody(rangeName));
    }

    BodyInserter<Void, ReactiveHttpOutputMessage> exportBody(String rangeName) {
        return (outputMessage, context) -> Mono.usingWhen(
                io(() -> exportService.create(rangeName)),
                file -> {
                    outputMessage.getHeaders().setContentLength(file.sizeBytes());
                    outputMessage.getHeaders().set(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + file.fileName() + "\"");
                    var content = exportContentReader.read(
                        file.path(), outputMessage.bufferFactory());
                    return outputMessage.writeWith(content);
                },
                this::closeExport,
                (file, ignored) -> closeExport(file),
                this::closeExport
            )
            .onErrorResume(error -> writeExportError(outputMessage, error));
    }

    private Mono<Void> closeExport(MonitoringExportService.ExportFile file) {
        return Mono.fromRunnable(file::close)
            .subscribeOn(ioScheduler.scheduler())
            .then();
    }

    private Mono<Void> writeExportError(
        ReactiveHttpOutputMessage outputMessage,
        Throwable error
    ) {
        if (outputMessage instanceof org.springframework.http.server.reactive.ServerHttpResponse
            serverResponse) {
            if (serverResponse.isCommitted()) {
                return Mono.error(error);
            }
            InsightApiErrorMapper.ApiError apiError = InsightApiErrorMapper.map(error);
            logFailure(error, apiError);
            serverResponse.setStatusCode(apiError.status());
            serverResponse.getHeaders().remove(HttpHeaders.CONTENT_LENGTH);
            serverResponse.getHeaders().remove(HttpHeaders.CONTENT_DISPOSITION);
            serverResponse.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            byte[] json = ("{\"error\":\"" + jsonEscape(apiError.code())
                + "\",\"message\":\"" + jsonEscape(apiError.message()) + "\"}")
                .getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = serverResponse.bufferFactory().wrap(json);
            return serverResponse.writeWith(Mono.just(buffer));
        }
        return Mono.error(error);
    }

    private static String jsonEscape(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private <T> Mono<ServerResponse> json(Callable<T> operation) {
        return io(operation)
            .flatMap(result -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
                .bodyValue(result))
            .onErrorResume(this::errorResponse);
    }

    private <T> Mono<T> io(Callable<T> operation) {
        return Mono.fromCallable(operation).subscribeOn(ioScheduler.scheduler());
    }

    private Mono<ServerResponse> errorResponse(Throwable error) {
        InsightApiErrorMapper.ApiError apiError = InsightApiErrorMapper.map(error);
        logFailure(error, apiError);
        return ServerResponse.status(apiError.status()).contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("error", apiError.code(), "message", apiError.message()));
    }

    private static String query(ServerRequest request, String name, String defaultValue) {
        return InsightApiInput.query(
            request, name, defaultValue, InsightApiInput.ENUM_MAX_LENGTH);
    }

    private static int integerQuery(ServerRequest request, String name, int defaultValue) {
        return InsightApiInput.integerQuery(request, name, defaultValue);
    }

    private static void logFailure(
        Throwable error,
        InsightApiErrorMapper.ApiError apiError
    ) {
        if (apiError.status().is5xxServerError()) {
            log.warn("Insight API request failed ({})",
                InsightApiErrorMapper.failureType(error));
        }
    }

    @FunctionalInterface
    interface ExportContentReader {

        Flux<DataBuffer> read(Path path, DataBufferFactory bufferFactory);
    }
}
