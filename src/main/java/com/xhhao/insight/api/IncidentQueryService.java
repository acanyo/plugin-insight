package com.xhhao.insight.api;

import com.xhhao.insight.alert.AlertEngine;
import com.xhhao.insight.api.model.AlertPageResponse;
import com.xhhao.insight.api.model.EventPageResponse;
import com.xhhao.insight.storage.database.SqliteStorageLifecycle;
import com.xhhao.insight.storage.model.AlertIncident;
import com.xhhao.insight.storage.model.AlertIncidentCursor;
import com.xhhao.insight.storage.model.AlertIncidentPage;
import com.xhhao.insight.storage.repository.AlertIncidentRepository;
import com.xhhao.insight.storage.repository.MonitorEventRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Reads the event timeline and alert incident history. */
@Component
public class IncidentQueryService {

    private final SqliteStorageLifecycle storage;
    private final MonitorEventRepository events;
    private final AlertIncidentRepository alerts;
    private final AlertEngine alertEngine;
    private final EventCursorCodec cursors;
    private final AlertCursorCodec alertCursors = new AlertCursorCodec();
    private final Clock clock;

    @Autowired
    public IncidentQueryService(
        SqliteStorageLifecycle storage,
        MonitorEventRepository events,
        AlertIncidentRepository alerts,
        AlertEngine alertEngine,
        EventCursorCodec cursors
    ) {
        this(storage, events, alerts, alertEngine, cursors, Clock.systemUTC());
    }

    IncidentQueryService(
        SqliteStorageLifecycle storage,
        MonitorEventRepository events,
        AlertIncidentRepository alerts,
        AlertEngine alertEngine,
        EventCursorCodec cursors,
        Clock clock
    ) {
        this.storage = storage;
        this.events = events;
        this.alerts = alerts;
        this.alertEngine = alertEngine;
        this.cursors = cursors;
        this.clock = clock;
    }

    public EventPageResponse events(
        String cursor,
        int requestedLimit,
        String type,
        String severity
    ) {
        if (!storage.health().available()) {
            return new EventPageResponse(List.of(), null);
        }
        int limit = Math.max(1, Math.min(200, requestedLimit));
        var page = events.findPage(
            0, clock.millis() + 1, type, severity, cursors.decode(cursor), limit);
        return new EventPageResponse(page.items(), cursors.encode(page.nextCursor()));
    }

    public List<AlertIncident> alerts(String status, int requestedLimit) {
        int limit = Math.max(1, Math.min(500, requestedLimit));
        if (status == null || status.isBlank() || "open".equalsIgnoreCase(status)
            || "acknowledged".equalsIgnoreCase(status)) {
            String normalized = status == null || status.isBlank()
                ? "OPEN" : status.toUpperCase(Locale.ROOT);
            return alertEngine.activeIncidents().stream()
                .filter(incident -> normalized.equalsIgnoreCase(incident.status()))
                .limit(limit)
                .toList();
        }
        if ("all".equalsIgnoreCase(status)) {
            List<AlertIncident> active = alertEngine.activeIncidents();
            if (!storage.health().available()) {
                return active.stream().limit(limit).toList();
            }
            List<AlertIncident> combined = new ArrayList<>(active);
            combined.addAll(alerts.findByStatus("RESOLVED", limit));
            return combined.stream()
                .sorted(Comparator.comparingLong(AlertIncident::updatedAtMs).reversed()
                    .thenComparing(AlertIncident::id, Comparator.reverseOrder()))
                .limit(limit)
                .toList();
        }
        if (!storage.health().available()) {
            return List.of();
        }
        return alerts.findByStatus(status.toUpperCase(Locale.ROOT), limit);
    }

    /** Returns a keyset-paginated alert history without changing the legacy array endpoint. */
    public AlertPageResponse alertHistory(
        String status,
        String cursor,
        int requestedLimit
    ) {
        int limit = Math.max(1, Math.min(200, requestedLimit));
        AlertIncidentCursor before = alertCursors.decode(cursor);
        String normalized = status == null || status.isBlank()
            ? "ALL" : status.toUpperCase(Locale.ROOT);
        if (!List.of("ALL", "OPEN", "ACKNOWLEDGED", "RESOLVED").contains(normalized)) {
            throw new IllegalArgumentException("Unsupported alert status");
        }

        List<AlertIncident> active = new ArrayList<>();
        if ("ALL".equals(normalized) || "OPEN".equals(normalized)
            || "ACKNOWLEDGED".equals(normalized)) {
            active.addAll(alertEngine.activeIncidents().stream()
                .filter(incident -> "ALL".equals(normalized)
                    || normalized.equalsIgnoreCase(incident.status()))
                .toList());
        }
        AlertIncidentPage persisted = new AlertIncidentPage(List.of(), null);
        if (storage.health().available()) {
            // Leave room for active incidents that may sort ahead of the persisted rows. This
            // keeps a keyset page complete and preserves the repository's has-more signal.
            int fetchLimit = Math.min(500, limit + active.size() + 1);
            persisted = alerts.findPage(
                "ALL".equals(normalized) ? null : normalized, before, fetchLimit);
        }

        // Active incidents can also be persisted. Prefer the in-memory representation.
        var unique = new LinkedHashMap<String, AlertIncident>();
        for (AlertIncident incident : active) {
            if (before != null && !isBefore(incident, before)) {
                continue;
            }
            unique.putIfAbsent(incident.id(), incident);
        }
        for (AlertIncident incident : persisted.items()) {
            if (before != null && !isBefore(incident, before)) {
                continue;
            }
            unique.putIfAbsent(incident.id(), incident);
        }
        List<AlertIncident> sorted = unique.values().stream()
            .sorted(Comparator.comparingLong(AlertIncident::updatedAtMs).reversed()
                .thenComparing(AlertIncident::id, Comparator.reverseOrder()))
            .limit(limit + 1L)
            .toList();
        boolean hasMore = sorted.size() > limit || persisted.nextCursor() != null;
        int pageSize = Math.min(limit, sorted.size());
        List<AlertIncident> items = sorted.subList(0, pageSize);
        AlertIncidentCursor next = hasMore && !items.isEmpty()
            ? new AlertIncidentCursor(items.get(items.size() - 1).updatedAtMs(),
                items.get(items.size() - 1).id()) : null;
        return new AlertPageResponse(items, alertCursors.encode(next));
    }

    private static boolean isBefore(AlertIncident incident, AlertIncidentCursor cursor) {
        return incident.updatedAtMs() < cursor.updatedAtMs()
            || (incident.updatedAtMs() == cursor.updatedAtMs()
                && incident.id().compareTo(cursor.id()) < 0);
    }
}
