package com.xhhao.insight.alert;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/** Encodes the metric and the evidence used to create an alert snapshot. */
@Slf4j
final class AlertIncidentDetailsEncoder {

    private static final ObjectMapper JSON = new ObjectMapper();

    private AlertIncidentDetailsEncoder() {}

    static String encode(AlertRule rule, AlertEvaluationInput input, double threshold) {
        double value = rule.currentValue(input);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("metric", rule.metricKey());
        details.put("unit", rule.unit());
        details.put("value", value);
        details.put("threshold", threshold);
        if (rule == AlertRule.DEPENDENCY_FAILURE) {
            details.put("dependencies", input.failedDependencies());
        }
        try {
            return JSON.writeValueAsString(details);
        } catch (JsonProcessingException error) {
            log.warn("Failed to encode Insight alert details for {}", rule.ruleId(), error);
            return null;
        }
    }
}
