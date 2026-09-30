package com.xhhao.insight.alert;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xhhao.insight.halo.DependencyProvider;
import com.xhhao.insight.halo.DependencyStatus;
import com.xhhao.insight.storage.model.AlertIncident;
import java.util.List;
import org.junit.jupiter.api.Test;

class DependencyAlertEvidenceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void includesDependencyReasonAndProviderInSummaryAndDetails() throws Exception {
        AlertEvaluationInput input = failingDependencyInput();

        String summary = AlertRule.DEPENDENCY_FAILURE.activeSummary(input, 0);
        assertThat(summary)
            .contains("搜索服务", "搜索引擎暂不可用", "搜索插件（search-x）");

        JsonNode details = JSON.readTree(AlertIncidentDetailsEncoder.encode(
            AlertRule.DEPENDENCY_FAILURE, input, 0));
        assertThat(details.path("dependencies").get(0).path("key").asText())
            .isEqualTo("search");
        assertThat(details.path("dependencies").get(0).path("providers").get(0)
            .path("pluginId").asText()).isEqualTo("search-x");
    }

    @Test
    void keepsLastFailureEvidenceAfterRecovery() {
        AlertIncident opened = AlertIncidentTransitions.open(
            AlertRule.DEPENDENCY_FAILURE, failingDependencyInput(), 0, 1_000, 2_000);
        AlertEvaluationInput recoveredInput = new AlertEvaluationInput(
            0, 0, 0, 0, 0, 0,
            Double.NaN, Double.NaN, 0, Double.NaN, Double.NaN,
            List.of());

        AlertIncident resolved = AlertIncidentTransitions.resolve(
            opened, AlertRule.DEPENDENCY_FAILURE, recoveredInput, 0, 3_000);

        assertThat(resolved.detailsJson()).isEqualTo(opened.detailsJson());
        assertThat(resolved.summary()).contains("已恢复");
        assertThat(resolved.lastValue()).isZero();
    }

    private static AlertEvaluationInput failingDependencyInput() {
        DependencyStatus search = DependencyStatus.warning(
                "search", "搜索服务", "搜索引擎暂不可用")
            .withProviders(List.of(new DependencyProvider("search-x", "搜索插件")));
        return new AlertEvaluationInput(
            0, 0, 0, 0, 0, 0,
            Double.NaN, Double.NaN, 1, Double.NaN, Double.NaN,
            List.of(search));
    }
}
