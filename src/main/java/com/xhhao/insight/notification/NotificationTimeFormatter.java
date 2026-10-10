package com.xhhao.insight.notification;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Formats notification timestamps in Beijing time (China Standard Time). */
final class NotificationTimeFormatter {

    private static final ZoneId BEIJING_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter OFFSET_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private NotificationTimeFormatter() {
    }

    static String format(Instant occurredAt) {
        return occurredAt == null ? "" : OFFSET_FORMATTER.format(occurredAt.atZone(BEIJING_ZONE));
    }
}
