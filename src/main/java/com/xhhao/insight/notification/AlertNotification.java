package com.xhhao.insight.notification;

import java.time.Instant;

public record AlertNotification(
    String incidentId,
    String title,
    String severity,
    String message,
    String metric,
    double value,
    double threshold,
    Instant occurredAt,
    boolean recovered
) {}
