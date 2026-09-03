package com.xhhao.insight.metrics;

import com.xhhao.insight.event.RequestFailureEventBuffer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import reactor.core.publisher.SignalType;
import reactor.core.publisher.Mono;
import run.halo.app.security.AdditionalWebFilter;

@Component
public class RequestObservationFilter implements AdditionalWebFilter {

    private static final String UNMATCHED_SCOPE = "_unmatched";
    private static final String UNMATCHED_ROUTE = "/{unmatched}";
    private static final String UNATTRIBUTED_SCOPE_PREFIX = "_unattributed:";

    private final RequestPathClassifier classifier;
    private final RequestOwnerResolver ownerResolver;
    private final RequestMetricsRegistry registry;
    private final RequestFailureEventBuffer requestFailures;

    public RequestObservationFilter(
        RequestPathClassifier classifier,
        RequestOwnerResolver ownerResolver,
        RequestMetricsRegistry registry,
        RequestFailureEventBuffer requestFailures
    ) {
        this.classifier = classifier;
        this.ownerResolver = ownerResolver;
        this.registry = registry;
        this.requestFailures = requestFailures;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var classification = classifier.classify(exchange.getRequest().getPath().value());
        if (!classification.monitored()) {
            return chain.filter(exchange);
        }
        long startedAt = System.nanoTime();
        var failureType = new AtomicReference<String>();
        return chain.filter(exchange)
            .doOnError(error -> failureType.set(error.getClass().getName()))
            .doFinally(signalType -> {
                String matchedRoute = matchedRoute(exchange);
                RequestMetricDimensions dimensions = RequestMetricDimensions.normalize(
                    matchedRoute == null ? UNMATCHED_SCOPE
                        : resolveScope(exchange, classification),
                    matchedRoute == null ? UNMATCHED_ROUTE : matchedRoute,
                    exchange.getRequest().getMethod().name());
                int statusCode = statusCode(exchange, failureType.get() != null, signalType);
                registry.record(dimensions, statusCode,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
                if (statusCode >= 500 || failureType.get() != null) {
                    requestFailures.record(dimensions, statusCode, failureType.get());
                }
            });
    }

    private String resolveScope(
        ServerWebExchange exchange,
        RequestPathClassifier.Classification classification
    ) {
        return ownerResolver.pluginId(exchange, classification.inferredPluginScope())
            .orElseGet(() -> classification.inferredPluginScope()
                ? UNATTRIBUTED_SCOPE_PREFIX + classification.scope()
                : classification.scope());
    }

    private static int statusCode(
        ServerWebExchange exchange,
        boolean failed,
        SignalType signalType
    ) {
        var status = exchange.getResponse().getStatusCode();
        if (status != null) {
            return status.value();
        }
        if (signalType == SignalType.CANCEL) {
            return 499;
        }
        return failed ? 500 : 200;
    }

    private static String matchedRoute(ServerWebExchange exchange) {
        Object pattern = exchange.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern == null) {
            pattern = exchange.getAttribute(RouterFunctions.MATCHING_PATTERN_ATTRIBUTE);
        }
        if (pattern instanceof PathPattern pathPattern) {
            return pathPattern.getPatternString();
        }
        return pattern == null ? null : pattern.toString();
    }
}
