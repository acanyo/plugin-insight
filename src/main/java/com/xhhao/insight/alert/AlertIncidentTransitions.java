package com.xhhao.insight.alert;

import com.xhhao.insight.storage.model.AlertIncident;
import java.util.UUID;

/**
 * Pure alert incident lifecycle transitions.
 */
final class AlertIncidentTransitions {

    private AlertIncidentTransitions() {}

    static AlertIncident open(
        AlertRule rule,
        AlertEvaluationInput input,
        double threshold,
        long breachedAtMs,
        long nowMs
    ) {
        double value = rule.currentValue(input);
        return new AlertIncident(
            UUID.randomUUID().toString(),
            rule.ruleId(),
            AlertStatus.OPEN.name(),
            rule.severity(),
            rule.title(),
            rule.activeSummary(input, threshold),
            fingerprint(rule),
            breachedAtMs,
            nowMs,
            null,
            1,
            value,
            threshold,
            AlertIncidentDetailsEncoder.encode(rule, input, threshold)
        );
    }

    static AlertIncident update(
        AlertIncident incident,
        AlertRule rule,
        AlertEvaluationInput input,
        double threshold,
        long nowMs
    ) {
        double value = rule.currentValue(input);
        return new AlertIncident(
            incident.id(),
            incident.ruleId(),
            incident.status(),
            incident.severity(),
            incident.title(),
            rule.activeSummary(input, threshold),
            incident.fingerprint(),
            incident.triggeredAtMs(),
            nowMs,
            null,
            increment(incident.occurrenceCount()),
            value,
            threshold,
            AlertIncidentDetailsEncoder.encode(rule, input, threshold)
        );
    }

    static AlertIncident resolve(
        AlertIncident incident,
        AlertRule rule,
        AlertEvaluationInput input,
        double threshold,
        long nowMs
    ) {
        double value = rule.currentValue(input);
        String details = rule == AlertRule.DEPENDENCY_FAILURE
            ? incident.detailsJson()
            : AlertIncidentDetailsEncoder.encode(rule, input, threshold);
        return new AlertIncident(
            incident.id(),
            incident.ruleId(),
            AlertStatus.RESOLVED.name(),
            incident.severity(),
            incident.title(),
            rule.recoveredSummary(value, threshold),
            incident.fingerprint(),
            incident.triggeredAtMs(),
            nowMs,
            nowMs,
            incident.occurrenceCount(),
            value,
            threshold,
            details
        );
    }

    static AlertIncident acknowledge(AlertIncident incident, long nowMs) {
        return new AlertIncident(
            incident.id(),
            incident.ruleId(),
            AlertStatus.ACKNOWLEDGED.name(),
            incident.severity(),
            incident.title(),
            incident.summary(),
            incident.fingerprint(),
            incident.triggeredAtMs(),
            nowMs,
            incident.resolvedAtMs(),
            incident.occurrenceCount(),
            incident.lastValue(),
            incident.thresholdValue(),
            incident.detailsJson()
        );
    }

    private static String fingerprint(AlertRule rule) {
        return "insight-alert:" + rule.ruleId();
    }

    private static long increment(long value) {
        return value == Long.MAX_VALUE ? value : value + 1;
    }

}
