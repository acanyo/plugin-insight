package com.xhhao.insight.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.xhhao.insight.halo.HaloRuntimeSnapshot;
import com.xhhao.insight.halo.PluginIdentityResolver;
import com.xhhao.insight.halo.PluginRuntimeInfo;
import com.xhhao.insight.runtime.MonitoringState;
import com.xhhao.insight.storage.model.PluginResourcePersistenceSummary;
import com.xhhao.insight.storage.model.ResourceAttributionSummary;
import com.xhhao.insight.storage.repository.PluginMetricRepository;
import com.xhhao.insight.storage.repository.PluginResourceRepository;

import org.junit.jupiter.api.Test;

import java.util.List;

class PressureScoreBreakdownTest {
    @Test
    void explainsNinePointsFromResidentThreadsWithoutChangingTheScore() {
        var resources = mock(PluginResourceRepository.class);
        when(resources.findPersistence(0, 3_600_000, 2000))
                .thenReturn(
                        List.of(
                                new PluginResourcePersistenceSummary(
                                        "insight", 60, 0, 0, 60, 0, 0, 0, 0, 4)));
        when(resources.summarizeCoverage(0, 3_600_000))
                .thenReturn(ResourceAttributionSummary.empty());
        var requests = mock(PluginMetricRepository.class);
        when(requests.findRequestPersistence(0, 3_600_000, 2000)).thenReturn(List.of());
        var state = new MonitoringState();
        state.updateHalo(
                new HaloRuntimeSnapshot(
                        "2.26",
                        null,
                        List.of(
                                new PluginRuntimeInfo(
                                        "insight", "Insight", "1.2.0", null, true, "STARTED", null)),
                        List.of()));
        var service =
                new PluginConsumerService(resources, requests, state, new PluginIdentityResolver());
        var item = service.find(0, 3_600_000).getFirst();
        assertThat(item.pressureScore()).isCloseTo(9.1, within(1e-9));
        assertThat(item.breakdown().cpuActiveMinutes()).isZero();
        assertThat(item.breakdown().threadPresentMinutes()).isEqualTo(60);
        assertThat(
                        item.breakdown().components().stream()
                                .mapToDouble(PressureScoreBreakdown.Component::contribution)
                                .sum())
                .isCloseTo(item.pressureScore(), within(1e-9));
        assertThat(item.sustained()).isFalse();
    }

    @Test
    void splitsTiedMaximumContributionsAndHandlesZeroSamples() {
        var tied =
                PressureScoreBreakdown.of(
                        20, 20, 0, 0, 20, 20, 0, 1, 1, 0, 10, 64 * 1024 * 1024, 0, 1, 1, 1);
        assertThat(tied.components().getFirst().contribution()).isEqualTo(8.75);
        assertThat(tied.components().get(1).contribution()).isEqualTo(8.75);
        var empty = PressureScoreBreakdown.of(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        assertThat(empty.components())
                .allSatisfy(component -> assertThat(component.contribution()).isZero());
    }
}
