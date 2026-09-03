package com.xhhao.insight.alert;

import static org.assertj.core.api.Assertions.assertThat;

import com.xhhao.insight.config.AlertSettings;
import org.junit.jupiter.api.Test;

class AlertRuleTest {

    @Test
    void evaluatesEveryBuiltInMetricAgainstItsConfiguredThreshold() {
        AlertSettings settings = settings();
        AlertEvaluationInput input = new AlertEvaluationInput(
            80, 81, 82, 5, 1_500, 1);

        assertThat(AlertRule.SYSTEM_CPU.breached(input, settings)).isTrue();
        assertThat(AlertRule.JVM_HEAP.breached(input, settings)).isTrue();
        assertThat(AlertRule.DISK_USED.breached(input, settings)).isTrue();
        assertThat(AlertRule.REQUEST_ERROR_RATE.breached(input, settings)).isTrue();
        assertThat(AlertRule.REQUEST_P95.breached(input, settings)).isTrue();
        assertThat(AlertRule.JVM_DEADLOCK.breached(input, settings)).isTrue();
    }

    @Test
    void treatsNoDeadlocksAndBelowThresholdMetricsAsHealthy() {
        AlertSettings settings = settings();
        AlertEvaluationInput input = new AlertEvaluationInput(
            79.99, 80.99, 81.99, 4.99, 1_499, 0);

        for (AlertRule rule : AlertRule.values()) {
            assertThat(rule.breached(input, settings))
                .as(rule.ruleId())
                .isFalse();
        }
    }

    @Test
    void sanitizesUnavailableOrOutOfRangeSamples() {
        AlertEvaluationInput input = new AlertEvaluationInput(
            Double.NaN, -10, 120, Double.POSITIVE_INFINITY, -5, -1);

        assertThat(input.systemCpuPercent()).isZero();
        assertThat(input.heapUsedPercent()).isZero();
        assertThat(input.diskUsedPercent()).isEqualTo(100);
        assertThat(input.requestErrorRatePercent()).isZero();
        assertThat(input.requestP95Millis()).isZero();
        assertThat(input.deadlockedThreadCount()).isZero();
    }

    private static AlertSettings settings() {
        AlertSettings settings = new AlertSettings();
        settings.setCpuWarningPercent(80);
        settings.setHeapWarningPercent(81);
        settings.setDiskWarningPercent(82);
        settings.setErrorRatePercent(5);
        settings.setP95WarningMillis(1_500);
        return settings;
    }
}
