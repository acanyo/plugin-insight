package com.xhhao.insight.halo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.github.zafarkhaja.semver.Version;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.app.core.extension.Plugin;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.infra.SystemInfoGetter;
import run.halo.app.plugin.extensionpoint.ExtensionGetter;
import run.halo.app.search.SearchEngine;

@ExtendWith(MockitoExtension.class)
class HaloRuntimeServiceTest {

    @Mock
    ReactiveExtensionClient extensionClient;

    @Mock
    ExtensionGetter extensionGetter;

    @Mock
    SystemInfoGetter systemInfoGetter;

    @Mock
    AttachmentStorageProbe attachmentStorageProbe;

    HaloRuntimeService service;

    @BeforeEach
    void setUp() {
        service = new HaloRuntimeService(extensionClient, extensionGetter, systemInfoGetter,
            attachmentStorageProbe);
    }

    @Test
    void shouldExposeAttachmentStorageProbeResult() {
        stubSystemInfo();
        when(extensionClient.listAll(eq(Plugin.class), any(ListOptions.class), any()))
            .thenReturn(Flux.empty());
        when(extensionClient.countBy(eq(Plugin.class), any(ListOptions.class)))
            .thenReturn(Mono.just(0L));
        when(extensionGetter.getEnabledExtension(SearchEngine.class)).thenReturn(Mono.empty());
        when(attachmentStorageProbe.probe()).thenReturn(Mono.just(
            DependencyStatus.healthy("attachments", "附件存储", "附件访问端点可达")));

        StepVerifier.create(service.snapshot())
            .assertNext(snapshot -> {
                DependencyStatus attachments = snapshot.dependencies().stream()
                    .filter(dependency -> "attachments".equals(dependency.key()))
                    .findFirst()
                    .orElseThrow();
                assertThat(attachments.status()).isEqualTo("healthy");
                assertThat(attachments.message()).contains("附件访问端点可达");
            })
            .verifyComplete();
    }

    @Test
    void shouldFailSnapshotWhenSystemInformationQueryFails() {
        RuntimeException queryFailure = new RuntimeException("system unavailable");
        when(systemInfoGetter.get()).thenReturn(Mono.error(queryFailure));
        when(extensionClient.listAll(eq(Plugin.class), any(ListOptions.class), any()))
            .thenReturn(Flux.empty());
        stubDependencyQueries();

        StepVerifier.create(service.snapshot())
            .expectErrorSatisfies(error -> {
                assertThat(error).isInstanceOf(IllegalStateException.class)
                    .hasMessage("Halo system information query failed")
                    .hasCause(queryFailure);
            })
            .verify();
    }

    @Test
    void shouldFailSnapshotWhenPluginQueryFails() {
        stubSystemInfo();
        RuntimeException queryFailure = new RuntimeException("plugin query unavailable");
        when(extensionClient.listAll(eq(Plugin.class), any(ListOptions.class), any()))
            .thenReturn(Flux.error(queryFailure));
        stubDependencyQueries();

        StepVerifier.create(service.snapshot())
            .expectErrorSatisfies(error -> {
                assertThat(error).isInstanceOf(IllegalStateException.class)
                    .hasMessage("Halo plugin status query failed")
                    .hasCause(queryFailure);
            })
            .verify();
    }

    private void stubSystemInfo() {
        var systemInfo = new run.halo.app.infra.SystemInfo()
            .setVersion(Version.parse("2.26.0"))
            .setActivatedThemeName("theme-earth");
        when(systemInfoGetter.get()).thenReturn(Mono.just(systemInfo));
    }

    private void stubDependencyQueries() {
        when(extensionClient.countBy(eq(Plugin.class), any(ListOptions.class)))
            .thenReturn(Mono.just(0L));
        when(extensionGetter.getEnabledExtension(SearchEngine.class)).thenReturn(Mono.empty());
        when(attachmentStorageProbe.probe()).thenReturn(Mono.just(
            DependencyStatus.warning("attachments", "附件存储", "未配置存储策略")));
    }
}
