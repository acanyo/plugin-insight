package com.xhhao.insight.api;

import com.xhhao.insight.alert.AlertEngine;
import com.xhhao.insight.storage.model.AlertIncident;
import org.springframework.stereotype.Component;

/** Applies state transitions to alert incidents. */
@Component
public class AlertCommandService {

    private static final int MAX_INCIDENT_ID_LENGTH = 128;

    private final AlertEngine alertEngine;

    public AlertCommandService(AlertEngine alertEngine) {
        this.alertEngine = alertEngine;
    }

    public AlertIncident acknowledge(String id) {
        if (id == null || id.isBlank() || id.length() > MAX_INCIDENT_ID_LENGTH
            || id.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid alert incident id");
        }
        return alertEngine.acknowledge(id);
    }
}
