package com.xhhao.insight.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.xhhao.insight.halo.HaloRuntimeSnapshot;
import com.xhhao.insight.halo.PluginIdentityResolver;
import com.xhhao.insight.halo.PluginRuntimeInfo;
import com.xhhao.insight.runtime.MonitoringState;
import com.xhhao.insight.resource.PluginResourceAttributionState;
import com.xhhao.insight.storage.model.PluginMetricSummary;
import com.xhhao.insight.storage.model.PluginResourceSummary;
import com.xhhao.insight.storage.model.ResourceAttributionSummary;
import com.xhhao.insight.storage.repository.PluginMetricRepository;
import com.xhhao.insight.storage.repository.PluginResourceRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class PluginRankingServiceTest {

    @Test
    void onlyAttributesMetricsWithAnActualHaloPluginIdentity() {
        PluginMetricRepository repository = mock(PluginMetricRepository.class);
        PluginResourceRepository resourceRepository = mock(PluginResourceRepository.class);
        when(repository.findRanking(anyLong(), anyLong(), any(), anyInt())).thenReturn(List.of(
            summary("photo", 1_000, 1_000_000),
            summary("pluginphotos", 20, 200_000),
            summary("PluginLinks", 10, 100_000)
        ));
        MonitoringState state = new MonitoringState();
        state.updateHalo(new HaloRuntimeSnapshot("2.26.0", null, List.of(
            plugin("PluginPhotos", "图库"),
            plugin("PluginLinks", "链接")
        ), List.of()));
        PluginRankingService service = new PluginRankingService(
            repository, resourceRepository, state, new PluginIdentityResolver(),
            new PluginResourceAttributionState());
        when(resourceRepository.findRanking(anyLong(), anyLong(), any(), anyInt()))
            .thenReturn(List.of());
        when(resourceRepository.summarizeCoverage(anyLong(), anyLong()))
            .thenReturn(com.xhhao.insight.storage.model.ResourceAttributionSummary.empty());

        var result = service.find(new ApiTimeRange("1h", 0, 3_600_000), "load");

        assertThat(result).extracting(item -> item.pluginId())
            .containsExactly("PluginPhotos", "PluginLinks");
        assertThat(result).noneMatch(item -> item.pluginId().equals("photo"));
        assertThat(result.getFirst().loadSharePercent()).isEqualTo(200d / 300d * 100d);
    }

    @Test
    void exposesEstimatedResourceDeltasWithCapabilityAndCoverage() {
        PluginMetricRepository requests = mock(PluginMetricRepository.class);
        PluginResourceRepository resources = mock(PluginResourceRepository.class);
        when(requests.findRanking(anyLong(), anyLong(), any(), anyInt()))
            .thenReturn(List.of(summary("plugin-a", 2, 20_000)));
        when(resources.findRanking(anyLong(), anyLong(), any(), anyInt()))
            .thenReturn(List.of(new PluginResourceSummary(
                "plugin-a", 4, 8, 8, 4, 5, 2, 5_000_000, 4_000)));
        when(resources.summarizeCoverage(anyLong(), anyLong())).thenReturn(
            new ResourceAttributionSummary(4, 4, 4, 0, 40, 8,
                10_000_000, 5_000_000, 8_000, 4_000));
        MonitoringState state = new MonitoringState();
        state.updateHalo(new HaloRuntimeSnapshot("2.26.0", null,
            List.of(plugin("plugin-a", "插件 A")), List.of()));
        PluginRankingService service = new PluginRankingService(
            requests, resources, state, new PluginIdentityResolver(),
            new PluginResourceAttributionState());

        assertThat(service.find(new ApiTimeRange("1h", 0, 3_600_000), "cpu"))
            .singleElement()
            .satisfies(item -> {
                assertThat(item.estimated()).isTrue();
                assertThat(item.resourceAvailability()).isEqualTo("AVAILABLE");
                assertThat(item.resourceConfidence()).isEqualTo("MEDIUM");
                assertThat(item.resourceAttributionMethod())
                    .isEqualTo("thread-context-classloader");
                assertThat(item.cpuTimeAvailable()).isTrue();
                assertThat(item.allocatedMemoryAvailable()).isTrue();
                assertThat(item.cpuTimeMs()).isEqualTo(5);
                assertThat(item.cpuSharePercent()).isEqualTo(50);
                assertThat(item.allocatedMemoryBytes()).isEqualTo(4_000);
                assertThat(item.allocationSharePercent()).isEqualTo(50);
                assertThat(item.cpuAttributionCoveragePercent()).isEqualTo(50);
            });
    }

    private static PluginRuntimeInfo plugin(String name, String displayName) {
        return new PluginRuntimeInfo(name, displayName, "1.0.0", true, "STARTED", null);
    }

    private static PluginMetricSummary summary(String id, long requests, long durationUs) {
        return new PluginMetricSummary(
            id, 0, 0, 0, 0, requests, 0, durationUs, durationUs,
            0, 0, 0, 0);
    }
}
