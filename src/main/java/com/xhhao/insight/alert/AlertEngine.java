package com.xhhao.insight.alert;

import com.xhhao.insight.config.AlertSettings;
import com.xhhao.insight.notification.AlertNotification;
import com.xhhao.insight.notification.NotificationDispatcher;
import com.xhhao.insight.storage.capacity.HistoryWriteGate;
import com.xhhao.insight.storage.model.AlertIncident;
import com.xhhao.insight.storage.model.MonitorEvent;
import com.xhhao.insight.storage.repository.AlertIncidentRepository;
import com.xhhao.insight.storage.repository.MonitorEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Stateful evaluator for the built-in Insight alert rules.
 *
 * <p>The evaluator deliberately keeps its transition state in memory so a storage or
 * notification outage cannot interrupt metric collection. Persistent active incidents are
 * restored lazily once SQLite becomes available.</p>
 */
@Slf4j
@Component
public class AlertEngine {

    private static final int ACTIVE_INCIDENT_LOAD_LIMIT = 1_000;

    private final AlertIncidentRepository incidents;
    private final MonitorEventRepository events;
    private final NotificationDispatcher notifications;
    private final HistoryWriteGate writeGate;
    private final Clock clock;
    private final Map<AlertRule, Long> pendingSinceMs = new EnumMap<>(AlertRule.class);
    private final Map<AlertRule, AlertIncident> activeByRule = new EnumMap<>(AlertRule.class);
    private final Map<AlertRule, AlertIncident> deferredIncidentWrites =
        new EnumMap<>(AlertRule.class);
    private final Map<AlertRule, Long> lastNotificationMs = new EnumMap<>(AlertRule.class);

    private boolean activeIncidentsLoaded;

    @Autowired
    public AlertEngine(
        AlertIncidentRepository incidents,
        MonitorEventRepository events,
        NotificationDispatcher notifications,
        HistoryWriteGate writeGate
    ) {
        this(incidents, events, notifications, writeGate, Clock.systemUTC());
    }

    public AlertEngine(
        AlertIncidentRepository incidents,
        MonitorEventRepository events,
        NotificationDispatcher notifications,
        HistoryWriteGate writeGate,
        Clock clock
    ) {
        this.incidents = Objects.requireNonNull(incidents, "incidents must not be null");
        this.events = Objects.requireNonNull(events, "events must not be null");
        this.notifications = Objects.requireNonNull(notifications,
            "notifications must not be null");
        this.writeGate = Objects.requireNonNull(writeGate, "writeGate must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Evaluates one immutable metrics snapshot against all configured rules.
     */
    public synchronized void evaluate(AlertEvaluationInput input, AlertSettings settings) {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(settings, "settings must not be null");
        loadActiveIncidents();
        flushDeferredIncidentWrites();
        if (!settings.isEnabled()) {
            pendingSinceMs.clear();
            resolveDisabledRules(input, settings, Math.max(0, clock.millis()));
            return;
        }

        long nowMs = Math.max(0, clock.millis());
        long requiredDurationMs = Math.max(0, settings.getDurationSeconds()) * 1_000L;
        long cooldownMs = Math.max(0, settings.getCooldownMinutes()) * 60_000L;
        for (AlertRule rule : AlertRule.values()) {
            evaluateRule(rule, input, settings, nowMs, requiredDurationMs, cooldownMs);
        }
    }

    /**
     * Returns a point-in-time immutable view of open and acknowledged incidents.
     */
    public synchronized List<AlertIncident> activeIncidents() {
        loadActiveIncidents();
        return activeByRule.values().stream()
            .filter(AlertEngine::isActive)
            .sorted(Comparator.comparingLong(AlertIncident::updatedAtMs).reversed())
            .toList();
    }

    /**
     * Acknowledges an active incident. Repeated acknowledgement is idempotent.
     */
    public synchronized AlertIncident acknowledge(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        loadActiveIncidents();
        AlertIncident current = activeByRule.values().stream()
            .filter(incident -> incident.id().equals(id))
            .findFirst()
            .orElseGet(() -> findIncident(id));
        if (AlertStatus.RESOLVED.name().equalsIgnoreCase(current.status())
            || AlertStatus.ACKNOWLEDGED.name().equalsIgnoreCase(current.status())) {
            return current;
        }

        long nowMs = Math.max(0, clock.millis());
        AlertIncident acknowledged = AlertIncidentTransitions.acknowledge(current, nowMs);
        if (saveIncident(acknowledged) != IncidentWriteOutcome.PERSISTED) {
            throw new IllegalStateException("Unable to persist acknowledgement for alert " + id);
        }
        AlertRule.fromRuleId(current.ruleId()).ifPresent(rule -> {
            activeByRule.put(rule, acknowledged);
            deferredIncidentWrites.remove(rule);
        });
        saveTimelineEvent(
            acknowledged,
            "告警已确认：" + acknowledged.title(),
            acknowledged.summary(),
            "info",
            nowMs
        );
        return acknowledged;
    }

    /**
     * Drops all in-memory incident state after the persisted incident history is cleared.
     *
     * <p>The command that invokes this method owns deletion of the SQLite rows. Keeping the
     * state reset separate makes the ordering explicit: a failed database delete must not make
     * the live evaluator forget incidents that are still durable.</p>
     */
    public synchronized void clearIncidentState() {
        activeByRule.clear();
        pendingSinceMs.clear();
        deferredIncidentWrites.clear();
        lastNotificationMs.clear();
        // Do not reload the rows just removed when the next overview/evaluation is requested.
        activeIncidentsLoaded = true;
    }

    /**
     * Deletes durable incident history while holding the evaluator lock, then forgets the live
     * state. This keeps an evaluation from writing a new incident between the delete and reset.
     */
    public synchronized int clearIncidentHistory() {
        int deleted = incidents.deleteAll();
        clearIncidentState();
        return deleted;
    }

    private void evaluateRule(
        AlertRule rule,
        AlertEvaluationInput input,
        AlertSettings settings,
        long nowMs,
        long requiredDurationMs,
        long cooldownMs
    ) {
        double value = rule.currentValue(input);
        double threshold = rule.threshold(settings);
        AlertIncident active = activeByRule.get(rule);
        if (!rule.breached(input, settings)) {
            pendingSinceMs.remove(rule);
            if (active != null) {
                resolve(rule, active, value, threshold, nowMs);
            }
            return;
        }

        if (active != null) {
            update(rule, active, value, threshold, nowMs, cooldownMs);
            return;
        }

        long breachedAtMs = pendingSinceMs.compute(rule, (ignored, previous) ->
            previous == null || previous > nowMs ? nowMs : previous);
        if (nowMs - breachedAtMs < requiredDurationMs) {
            return;
        }
        open(rule, value, threshold, breachedAtMs, nowMs);
    }

    private void open(
        AlertRule rule,
        double value,
        double threshold,
        long breachedAtMs,
        long nowMs
    ) {
        AlertIncident opened = AlertIncidentTransitions.open(
            rule, value, threshold, breachedAtMs, nowMs);
        activeByRule.put(rule, opened);
        pendingSinceMs.remove(rule);
        saveRealtimeIncident(rule, opened);
        saveTimelineEvent(
            opened,
            "告警触发：" + rule.title(),
            opened.summary(),
            rule.severity(),
            nowMs
        );
        notify(rule, opened, false, nowMs);
    }

    private void update(
        AlertRule rule,
        AlertIncident active,
        double value,
        double threshold,
        long nowMs,
        long cooldownMs
    ) {
        AlertIncident updated = AlertIncidentTransitions.update(
            active, rule, value, threshold, nowMs);
        activeByRule.put(rule, updated);
        saveRealtimeIncident(rule, updated);

        if (AlertStatus.ACKNOWLEDGED.name().equalsIgnoreCase(updated.status())) {
            return;
        }
        Long lastNotifiedAt = lastNotificationMs.get(rule);
        if (lastNotifiedAt != null && (nowMs < lastNotifiedAt
            || nowMs - lastNotifiedAt < cooldownMs)) {
            return;
        }
        saveTimelineEvent(
            updated,
            "告警持续：" + rule.title(),
            updated.summary(),
            rule.severity(),
            nowMs
        );
        notify(rule, updated, false, nowMs);
    }

    private void resolve(
        AlertRule rule,
        AlertIncident active,
        double value,
        double threshold,
        long nowMs
    ) {
        AlertIncident resolved = AlertIncidentTransitions.resolve(
            active, rule, value, threshold, nowMs);
        if (saveRealtimeIncident(rule, resolved) == IncidentWriteOutcome.FAILED) {
            return;
        }
        activeByRule.remove(rule);
        pendingSinceMs.remove(rule);
        saveTimelineEvent(
            resolved,
            "告警恢复：" + rule.title(),
            resolved.summary(),
            "info",
            nowMs
        );
        dispatchNotification(rule, resolved, true, nowMs);
        lastNotificationMs.remove(rule);
    }

    private void notify(
        AlertRule rule,
        AlertIncident incident,
        boolean recovered,
        long nowMs
    ) {
        // Mark the attempt before dispatching so a broken channel cannot produce a retry storm.
        lastNotificationMs.put(rule, nowMs);
        dispatchNotification(rule, incident, recovered, nowMs);
    }

    private void dispatchNotification(
        AlertRule rule,
        AlertIncident incident,
        boolean recovered,
        long nowMs
    ) {
        AlertNotification notification = new AlertNotification(
            recovered ? "告警恢复：" + rule.title() : rule.title(),
            recovered ? "info" : rule.severity(),
            incident.summary(),
            rule.metricKey(),
            incident.lastValue() == null ? 0 : incident.lastValue(),
            incident.thresholdValue() == null ? 0 : incident.thresholdValue(),
            Instant.ofEpochMilli(nowMs),
            recovered
        );
        try {
            CompletableFuture<?> result = notifications.dispatch(notification);
            if (result != null) {
                result.exceptionally(error -> {
                    log.warn("Failed to dispatch Insight alert notification for {}",
                        rule.ruleId(), error);
                    return null;
                });
            }
        } catch (RuntimeException error) {
            log.warn("Failed to schedule Insight alert notification for {}", rule.ruleId(), error);
        }
    }

    private void loadActiveIncidents() {
        if (activeIncidentsLoaded) {
            return;
        }
        try {
            List<AlertIncident> persisted = new ArrayList<>();
            addIfPresent(persisted, incidents.findByStatus(
                AlertStatus.OPEN.name(), ACTIVE_INCIDENT_LOAD_LIMIT));
            addIfPresent(persisted, incidents.findByStatus(
                AlertStatus.ACKNOWLEDGED.name(), ACTIVE_INCIDENT_LOAD_LIMIT));
            for (AlertIncident incident : persisted) {
                AlertRule.fromRuleId(incident.ruleId()).ifPresent(rule -> {
                    AlertIncident previous = activeByRule.get(rule);
                    if (previous == null || previous.updatedAtMs() < incident.updatedAtMs()) {
                        activeByRule.put(rule, incident);
                        lastNotificationMs.put(rule, incident.updatedAtMs());
                    }
                });
            }
            activeIncidentsLoaded = true;
        } catch (RuntimeException error) {
            log.warn("Failed to restore active Insight alerts; evaluation will continue", error);
        }
    }

    private AlertIncident findIncident(String id) {
        try {
            return incidents.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found: " + id));
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new IllegalStateException("Unable to read alert incident: " + id, error);
        }
    }

    private IncidentWriteOutcome saveIncident(AlertIncident incident) {
        if (!writeGate.allowsWrite()) {
            return IncidentWriteOutcome.SKIPPED;
        }
        try {
            incidents.save(incident);
            return IncidentWriteOutcome.PERSISTED;
        } catch (RuntimeException error) {
            log.warn("Failed to persist Insight alert {}", incident.id(), error);
            return IncidentWriteOutcome.FAILED;
        }
    }

    private IncidentWriteOutcome saveRealtimeIncident(
        AlertRule rule,
        AlertIncident incident
    ) {
        IncidentWriteOutcome outcome = saveIncident(incident);
        if (outcome == IncidentWriteOutcome.SKIPPED) {
            // At most one deferred state per built-in rule is retained. This preserves a future
            // durable terminal state without turning a capacity pause into an unbounded queue.
            deferredIncidentWrites.put(rule, incident);
        } else if (outcome == IncidentWriteOutcome.PERSISTED) {
            deferredIncidentWrites.remove(rule);
        }
        return outcome;
    }

    private void flushDeferredIncidentWrites() {
        for (Map.Entry<AlertRule, AlertIncident> entry :
            List.copyOf(deferredIncidentWrites.entrySet())) {
            IncidentWriteOutcome outcome = saveIncident(entry.getValue());
            if (outcome == IncidentWriteOutcome.PERSISTED) {
                deferredIncidentWrites.remove(entry.getKey(), entry.getValue());
                continue;
            }
            // One shared gate and one SQLite connection mean later entries cannot make useful
            // progress after this rejection or failure during the same evaluation.
            return;
        }
    }

    private void resolveDisabledRules(
        AlertEvaluationInput input,
        AlertSettings settings,
        long nowMs
    ) {
        for (Map.Entry<AlertRule, AlertIncident> entry :
            List.copyOf(activeByRule.entrySet())) {
            AlertRule rule = entry.getKey();
            resolve(
                rule,
                entry.getValue(),
                rule.currentValue(input),
                rule.threshold(settings),
                nowMs
            );
        }
    }

    private void saveTimelineEvent(
        AlertIncident incident,
        String title,
        String summary,
        String severity,
        long nowMs
    ) {
        if (!writeGate.allowsWrite()) {
            return;
        }
        MonitorEvent event = new MonitorEvent(
            UUID.randomUUID().toString(),
            nowMs,
            "alert",
            severity,
            incident.ruleId(),
            title,
            summary,
            incident.fingerprint(),
            incident.triggeredAtMs(),
            nowMs,
            incident.occurrenceCount(),
            incident.detailsJson()
        );
        try {
            events.save(event);
        } catch (RuntimeException error) {
            log.warn("Failed to persist Insight alert timeline event for {}",
                incident.ruleId(), error);
        }
    }

    private static void addIfPresent(List<AlertIncident> target, List<AlertIncident> values) {
        if (values != null) {
            target.addAll(values);
        }
    }

    private static boolean isActive(AlertIncident incident) {
        return AlertStatus.OPEN.name().equalsIgnoreCase(incident.status())
            || AlertStatus.ACKNOWLEDGED.name().equalsIgnoreCase(incident.status());
    }

    private enum IncidentWriteOutcome {
        PERSISTED,
        SKIPPED,
        FAILED
    }
}
