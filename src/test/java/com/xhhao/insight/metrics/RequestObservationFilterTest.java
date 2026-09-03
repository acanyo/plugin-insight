package com.xhhao.insight.metrics;

import com.xhhao.insight.event.RequestFailureEventBuffer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.HandlerMapping;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RequestObservationFilterTest {

    @Test
    void shouldRecordClientCancellationAsFourHundredStatus() {
        RequestMetricsRegistry registry = new RequestMetricsRegistry();
        RequestObservationFilter filter = filter(registry);
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/posts/example"));

        StepVerifier.create(filter.filter(exchange, ignored -> Mono.never()))
            .thenCancel()
            .verify();

        assertThat(registry.currentSnapshot()).singleElement()
            .satisfies(metric -> {
                assertThat(metric.statusGroup()).isEqualTo(4);
                assertThat(metric.scope()).isEqualTo("_unmatched");
                assertThat(metric.route()).isEqualTo("/{unmatched}");
            });
    }

    @Test
    void shouldNeverPersistSegmentsFromUnmatchedApiLikePaths() {
        RequestMetricsRegistry registry = new RequestMetricsRegistry();
        RequestObservationFilter filter = filter(registry);
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get(
            "/apis/alice@example.com/v1alpha1/private-token/value"));

        StepVerifier.create(filter.filter(exchange, ignored -> Mono.empty()))
            .verifyComplete();

        assertThat(registry.currentSnapshot()).singleElement()
            .satisfies(metric -> {
                assertThat(metric.scope()).isEqualTo("_unmatched");
                assertThat(metric.route()).isEqualTo("/{unmatched}")
                    .doesNotContain("alice", "token", "value");
            });
    }

    @Test
    void shouldPreferFrameworkRouteTemplateAfterHandlerMapping() {
        RequestMetricsRegistry registry = new RequestMetricsRegistry();
        RequestObservationFilter filter = filter(registry);
        var exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/widgets/private-value"));

        StepVerifier.create(filter.filter(exchange, current -> {
                current.getAttributes().put(
                    HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/widgets/{name}");
                return Mono.empty();
            }))
            .verifyComplete();

        assertThat(registry.currentSnapshot()).singleElement()
            .satisfies(metric -> {
                assertThat(metric.scope()).isEqualTo("halo-core");
                assertThat(metric.route()).isEqualTo("/widgets/{name}");
            });
    }

    @Test
    void shouldAttributeMappedPluginHandlerToItsMetadataName() {
        RequestMetricsRegistry registry = new RequestMetricsRegistry();
        PluginManager pluginManager = mock(PluginManager.class);
        PluginWrapper currentPlugin = mock(PluginWrapper.class);
        PluginWrapper owner = mock(PluginWrapper.class);
        when(currentPlugin.getPluginManager()).thenReturn(pluginManager);
        when(owner.getPluginId()).thenReturn("PluginPhotos");
        when(pluginManager.whichPlugin(any(Class.class))).thenReturn(owner);
        RequestObservationFilter filter = new RequestObservationFilter(
            new RequestPathClassifier(),
            new RequestOwnerResolver(currentPlugin, mock(PluginApiOwnershipIndex.class)),
            registry,
            new RequestFailureEventBuffer());
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get(
            "/apis/console.api.photo.halo.run/v1alpha1/photos"));

        StepVerifier.create(filter.filter(exchange, current -> {
                current.getAttributes().put(
                    HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
                    "/apis/console.api.photo.halo.run/v1alpha1/photos");
                current.getAttributes().put(
                    HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE, new MappedPluginHandler());
                return Mono.empty();
            }))
            .verifyComplete();

        assertThat(registry.currentSnapshot()).singleElement()
            .satisfies(metric -> assertThat(metric.scope()).isEqualTo("PluginPhotos"));
    }

    @Test
    void shouldAttributeWrappedFunctionalHandlerFromItsCustomEndpointGroup() {
        RequestMetricsRegistry registry = new RequestMetricsRegistry();
        PluginManager pluginManager = mock(PluginManager.class);
        PluginWrapper currentPlugin = mock(PluginWrapper.class);
        when(currentPlugin.getPluginManager()).thenReturn(pluginManager);
        PluginApiOwnershipIndex apiOwners = mock(PluginApiOwnershipIndex.class);
        when(apiOwners.pluginId("console.api.appstore.halo.run/v1alpha1"))
            .thenReturn(Optional.of("app-store-integration"));
        RequestObservationFilter filter = new RequestObservationFilter(
            new RequestPathClassifier(),
            new RequestOwnerResolver(currentPlugin, apiOwners),
            registry,
            new RequestFailureEventBuffer());
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get(
            "/apis/console.api.appstore.halo.run/v1alpha1/installed-apps"));

        StepVerifier.create(filter.filter(exchange, current -> {
                current.getAttributes().put(
                    HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
                    "/apis/console.api.appstore.halo.run/v1alpha1/installed-apps");
                current.getAttributes().put(
                    HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE, new Object());
                return Mono.empty();
            }))
            .verifyComplete();

        assertThat(registry.currentSnapshot()).singleElement()
            .satisfies(metric -> assertThat(metric.scope())
                .isEqualTo("app-store-integration"));
    }

    @Test
    void shouldNotProjectAnInferredApiGroupAsAPluginIdentity() {
        RequestMetricsRegistry registry = new RequestMetricsRegistry();
        RequestObservationFilter filter = filter(registry);
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get(
            "/apis/console.api.photo.halo.run/v1alpha1/photos"));

        StepVerifier.create(filter.filter(exchange, current -> {
                current.getAttributes().put(
                    HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
                    "/apis/console.api.photo.halo.run/v1alpha1/photos");
                return Mono.empty();
            }))
            .verifyComplete();

        assertThat(registry.currentSnapshot()).singleElement()
            .satisfies(metric -> assertThat(metric.scope())
                .isEqualTo("_unattributed:photo"));
    }

    @Test
    void shouldBufferOnlyTheExceptionClassAndFrameworkRouteForFailedRequests() {
        RequestMetricsRegistry registry = new RequestMetricsRegistry();
        RequestFailureEventBuffer failures = new RequestFailureEventBuffer();
        RequestObservationFilter filter = filter(registry, failures);
        var exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/widgets/private-value?token=secret"));

        StepVerifier.create(filter.filter(exchange, current -> {
                current.getAttributes().put(
                    HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/widgets/{name}");
                return Mono.error(new IllegalStateException("private service url"));
            }))
            .expectError(IllegalStateException.class)
            .verify();

        assertThat(failures.drain()).singleElement().satisfies(event -> {
            assertThat(event.summary()).contains("/widgets/{name}").doesNotContain("private-value");
            assertThat(event.detailsJson())
                .contains("java.lang.IllegalStateException")
                .doesNotContain("private service url", "token", "secret");
        });
    }

    private static RequestObservationFilter filter(RequestMetricsRegistry registry) {
        return filter(registry, new RequestFailureEventBuffer());
    }

    private static RequestObservationFilter filter(
        RequestMetricsRegistry registry,
        RequestFailureEventBuffer failures
    ) {
        PluginWrapper currentPlugin = mock(PluginWrapper.class);
        when(currentPlugin.getPluginManager()).thenReturn(mock(PluginManager.class));
        return new RequestObservationFilter(
            new RequestPathClassifier(),
            new RequestOwnerResolver(currentPlugin, mock(PluginApiOwnershipIndex.class)),
            registry,
            failures);
    }

    private static final class MappedPluginHandler {}
}
