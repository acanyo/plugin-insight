package com.xhhao.insight.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class RequestPathClassifierTest {

    private final RequestPathClassifier classifier = new RequestPathClassifier();

    @Test
    void shouldIgnoreInsightAndStaticRequests() {
        assertThat(classifier.classify(
            "/apis/console.api.insight.halo.run/v1alpha1/overview").monitored()).isFalse();
        assertThat(classifier.classify("/plugins/insight/assets/app.js").monitored()).isFalse();
        assertThat(classifier.classify("/ui-assets/ui-plugin.json").monitored()).isFalse();
        assertThat(classifier.classify("/plugins/links/assets/manifest").monitored()).isFalse();
    }

    @Test
    void shouldMapPluginApiToPluginScope() {
        var result = classifier.classify(
            "/apis/console.api.links.halo.run/v1alpha1/links/abc-123");

        assertThat(result.monitored()).isTrue();
        assertThat(result.scope()).isEqualTo("links");
        assertThat(result.route()).doesNotContain("abc-123");
    }

    @Test
    void shouldClassifyPublicPluginApiInsteadOfHaloCore() {
        var apiPrefixed = classifier.classify(
            "/apis/api.links.halo.run/v1alpha1/links/example");
        var direct = classifier.classify(
            "/apis/links.halo.run/v1alpha1/links/example");

        assertThat(apiPrefixed.scope()).isEqualTo("links");
        assertThat(direct.scope()).isEqualTo("links");
        assertThat(apiPrefixed.monitored()).isTrue();
        assertThat(direct.monitored()).isTrue();
    }

    @Test
    void shouldKeepHaloOwnedApiGroupsInCoreScope() {
        assertThat(classifier.classify(
            "/apis/api.storage.halo.run/v1alpha1/attachments/example").scope())
            .isEqualTo("halo-core");
        assertThat(classifier.classify(
            "/apis/security.halo.run/v1alpha1/devices/example").scope())
            .isEqualTo("halo-core");
    }

    @Test
    void shouldNormalizeGenericIdentifiers() {
        var result = classifier.classify("/posts/42/comments/123");

        assertThat(result.scope()).isEqualTo("halo-core");
        assertThat(result.route()).isEqualTo("/posts/{id}/comments/{id}");
    }

    @Test
    void shouldNeverKeepUntrustedGenericPathValues() {
        assertThat(classifier.classify("/callback/alice@example.com/short-secret").route())
            .isEqualTo("/{route}/{segment}/{slug}")
            .doesNotContain("alice", "secret");
        assertThat(classifier.classify("/posts/private-draft").route())
            .isEqualTo("/posts/{slug}")
            .doesNotContain("private-draft");
    }

    @Test
    void shouldNotKeepApiSubresourceNames() {
        var result = classifier.classify(
            "/apis/api.links.halo.run/v1alpha1/links/public-api-key");

        assertThat(result.route())
            .isEqualTo("/apis/api.links.halo.run/v1alpha1/links/{name}")
            .doesNotContain("public-api-key");
    }

    @Test
    void shouldCollapseRandomSlugsWithoutLosingStaticParentRoute() {
        var routes = java.util.stream.IntStream.range(0, 100)
            .mapToObj(ignored -> classifier.classify(
                "/posts/article-" + UUID.randomUUID()).route())
            .distinct()
            .toList();

        assertThat(routes).containsExactly("/posts/{slug}");
        assertThat(classifier.classify("/posts/latest").route()).isEqualTo("/posts/latest");
    }

    @Test
    void shouldBoundLongPathSegmentsAndStillClassifyPluginApi() {
        String oversized = "segment".repeat(1_000);
        var generic = classifier.classify("/posts/" + oversized + "/" + oversized);
        var pluginApi = classifier.classify(
            "/apis/api.links.halo.run/v1alpha1/links/" + oversized);

        assertThat(generic.route()).hasSizeLessThanOrEqualTo(512);
        assertThat(pluginApi.scope()).isEqualTo("links");
        assertThat(pluginApi.route()).startsWith(
            "/apis/api.links.halo.run/v1alpha1/links/{name}");
    }

    @Test
    void shouldIgnoreCommonAssetsButKeepApiResourcesWithFileLikeNames() {
        assertThat(classifier.classify("/uploads/poster.avif").monitored()).isFalse();
        assertThat(classifier.classify("/media/demo.mp4").monitored()).isFalse();
        assertThat(classifier.classify("/sitemap.xml").monitored()).isFalse();

        var api = classifier.classify(
            "/apis/api.links.halo.run/v1alpha1/links/export.json");
        assertThat(api.monitored()).isTrue();
        assertThat(api.scope()).isEqualTo("links");
    }
}
