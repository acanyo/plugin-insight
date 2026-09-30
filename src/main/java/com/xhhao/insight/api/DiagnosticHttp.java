package com.xhhao.insight.api;

import java.util.Map;
import java.util.concurrent.Callable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
public class DiagnosticHttp {
    private final InsightIoScheduler io;
    public DiagnosticHttp(InsightIoScheduler io) { this.io = io; }
    public <T> Mono<ServerResponse> json(Callable<T> operation) {
        return Mono.fromCallable(operation).subscribeOn(io.scheduler())
            .flatMap(value -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(value))
            .onErrorResume(DiagnosticHttp::error);
    }
    public static Mono<ServerResponse> error(Throwable error) {
        var mapped = InsightApiErrorMapper.map(error);
        return ServerResponse.status(mapped.status()).bodyValue(Map.of("message", mapped.message(), "error", mapped.code()));
    }
    public static String query(ServerRequest request, String key, String fallback) {
        return InsightApiInput.query(request, key, fallback, 128);
    }
    public static String id(ServerRequest request) { return InsightApiInput.pathVariable(request, "id", 128); }
}
