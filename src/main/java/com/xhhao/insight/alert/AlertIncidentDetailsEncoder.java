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
        return encode(rule, input, threshold, null);
    }

    static String encode(AlertRule rule, AlertEvaluationInput input, double threshold, AlertSceneSnapshot scene) {
        double value = rule.currentValue(input);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("metric", rule.metricKey());
        details.put("unit", rule.unit());
        details.put("value", value);
        details.put("threshold", threshold);
        if (scene != null) { details.put("scene", scene); details.put("schemaVersion", 2); }
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

    static String preservingScene(AlertRule rule, AlertEvaluationInput input, double threshold, String previous) {
        String current = encode(rule, input, threshold);
        if (current == null || previous == null) return current;
        try {
            var before = JSON.readTree(previous);
            if (before == null || !before.isObject() || !before.has("scene")) return current;
            var payload = (com.fasterxml.jackson.databind.node.ObjectNode) JSON.readTree(current);
            payload.set("scene", before.get("scene")); payload.put("schemaVersion", 2);
            return JSON.writeValueAsString(payload);
        } catch (JsonProcessingException | ClassCastException malformed) { return current; }
    }
}
