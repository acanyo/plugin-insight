package com.xhhao.insight.alert;

/**
 * Lifecycle states persisted for an alert incident.
 */
public enum AlertStatus {
    OPEN,
    ACKNOWLEDGED,
    RESOLVED;

    public boolean active() {
        return this != RESOLVED;
    }
}
