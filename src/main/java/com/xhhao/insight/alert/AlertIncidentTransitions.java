package com.xhhao.insight.alert;

import com.xhhao.insight.storage.model.AlertIncident;
import java.util.Locale;
import java.util.UUID;

/**
 * Pure alert incident lifecycle transitions.
 */
final class AlertIncidentTransitions {

    private AlertIncidentTransitions() {}

    static AlertIncident open(
        AlertRule rule,
        double value,
        double threshold,
        long breachedAtMs,
        long nowMs
    ) {
        return new AlertIncident(
            UUID.randomUUID().toString(),
            rule.ruleId(),
            AlertStatus.OPEN.name(),
            rule.severity(),
            rule.title(),
            rule.activeSummary(value, threshold),
            fingerprint(rule),
            breachedAtMs,
            nowMs,
            null,
            1,
            value,
            threshold,
            details(rule, value, threshold)
        );
    }

    static AlertIncident update(
        AlertIncident incident,
        AlertRule rule,
        double value,
        double threshold,
        long nowMs
    ) {
        return new AlertIncident(
            incident.id(),
            incident.ruleId(),
            incident.status(),
            incident.severity(),
            incident.title(),
            rule.activeSummary(value, threshold),
            incident.fingerprint(),
            incident.triggeredAtMs(),
            nowMs,
            null,
            increment(incident.occurrenceCount()),
            value,
            threshold,
            details(rule, value, threshold)
        );
    }

    static AlertIncident resolve(
        AlertIncident incident,
        AlertRule rule,
        double value,
        double threshold,
        long nowMs
    ) {
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
            details(rule, value, threshold)
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

    private static String details(AlertRule rule, double value, double threshold) {
        return String.format(Locale.ROOT,
            "{\"metric\":\"%s\",\"unit\":\"%s\",\"value\":%.4f,\"threshold\":%.4f}",
            rule.metricKey(), rule.unit(), value, threshold);
    }
}
