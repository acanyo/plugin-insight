package com.xhhao.insight.api;

import com.xhhao.insight.alert.AlertEngine;
import com.xhhao.insight.alert.AlertStatus;
import com.xhhao.insight.notification.HaloNotificationReadService;
import com.xhhao.insight.storage.model.AlertIncident;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Applies state transitions to alert incidents. */
@Component
@Slf4j
public class AlertCommandService {

    private static final int MAX_INCIDENT_ID_LENGTH = 128;

    private final AlertEngine alertEngine;
    private final HaloNotificationReadService notificationReadService;

    public AlertCommandService(
        AlertEngine alertEngine,
        HaloNotificationReadService notificationReadService
    ) {
        this.alertEngine = alertEngine;
        this.notificationReadService = notificationReadService;
    }

    public AlertIncident acknowledge(String id) {
        if (id == null || id.isBlank() || id.length() > MAX_INCIDENT_ID_LENGTH
            || id.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid alert incident id");
        }
        AlertIncident incident = alertEngine.acknowledge(id);
        if (AlertStatus.ACKNOWLEDGED.name().equalsIgnoreCase(incident.status())) {
            markHaloNotificationsRead(incident);
        }
        return incident;
    }

    private void markHaloNotificationsRead(AlertIncident incident) {
        try {
            notificationReadService.markIncidentRead(incident);
        } catch (RuntimeException error) {
            // The incident is already durable; notification-center cleanup must not undo it.
            log.warn("Failed to mark Halo notifications as read for incident {} ({})",
                incident.id(), error.getClass().getSimpleName());
        }
    }
}
