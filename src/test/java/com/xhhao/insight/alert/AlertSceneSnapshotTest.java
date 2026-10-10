package com.xhhao.insight.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xhhao.insight.consumer.PluginConsumerService;
import com.xhhao.insight.runtime.MonitoringState;
import com.xhhao.insight.storage.model.RequestRouteSort;
import com.xhhao.insight.storage.repository.RequestMetricRepository;

import org.junit.jupiter.api.Test;

import java.util.List;

class AlertSceneSnapshotTest {
    @Test
    void preservesTheFirstSceneAcrossUpdatesRecoveryAndAcknowledgement() throws Exception {
        var scene =
                new AlertSceneSnapshot(
                        2000,
                        1900,
                        30d,
                        40d,
                        1d,
                        10,
                        100L,
                        1000,
                        2000,
                        List.of(),
                        List.of(),
                        "captured");
        var input = new AlertEvaluationInput(90, 40, 0, 0, 100, 0);
        var opened =
                AlertIncidentTransitions.open(AlertRule.SYSTEM_CPU, input, 80, 1000, 2000, scene);
        var updated =
                AlertIncidentTransitions.update(
                        opened,
                        AlertRule.SYSTEM_CPU,
                        new AlertEvaluationInput(95, 50, 0, 0, 200, 0),
                        80,
                        3000);
        var resolved =
                AlertIncidentTransitions.resolve(
                        updated,
                        AlertRule.SYSTEM_CPU,
                        new AlertEvaluationInput(20, 30, 0, 0, 50, 0),
                        80,
                        4000);
        var acknowledged = AlertIncidentTransitions.acknowledge(resolved, 5000);
        var json = new ObjectMapper();
        for (var incident : List.of(opened, updated, resolved, acknowledged)) {
            assertThat(
                            json.readTree(incident.detailsJson())
                                    .path("scene")
                                    .path("capturedAtMs")
                                    .asLong())
                    .isEqualTo(2000);
            assertThat(
                            json.readTree(incident.detailsJson())
                                    .path("scene")
                                    .path("processCpuPercent")
                                    .asDouble())
                    .isEqualTo(30);
        }
        assertThat(resolved.lastValue()).isEqualTo(20);
    }

    @Test
    void toleratesLegacyAndMalformedScenePayloads() throws Exception {
        var input = new AlertEvaluationInput(20, 30, 0, 0, 50, 0);
        var json = new ObjectMapper();
        for (String previous : List.of("", " ", "null", "[]", "{broken", "{\"metric\":\"cpu\"}")) {
            var encoded =
                    AlertIncidentDetailsEncoder.preservingScene(
                            AlertRule.SYSTEM_CPU, input, 80, previous);
            assertThat(json.readTree(encoded).path("value").asDouble()).isEqualTo(20);
            assertThat(json.readTree(encoded).has("scene")).isFalse();
        }
    }

    @Test
    void capturesOncePerEvaluationAndKeepsPartialEvidenceWhenStorageFails() {
        var consumers = mock(PluginConsumerService.class);
        when(consumers.find(0, 1001)).thenThrow(new IllegalStateException("storage failed"));
        var requests = mock(RequestMetricRepository.class);
        when(requests.findTopRoutes(0, 1001, RequestRouteSort.P95_DURATION, 5))
                .thenReturn(List.of());
        var service = new AlertSceneSnapshotService(new MonitoringState(), consumers, requests);
        var input = new AlertEvaluationInput(90, 40, 0, 0, 100, 0);
        var scene = service.capture(input, 1000);
        assertThat(service.capture(input, 1000)).isSameAs(scene);
        verify(consumers, times(1)).find(0, 1001);
        assertThat(scene.processCpuPercent()).isNull();
        assertThat(scene.gcPercent()).isNull();
        assertThat(scene.coverage()).contains("插件排行不可用", "系统采样不可用");
    }
}
