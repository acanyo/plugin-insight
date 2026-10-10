package com.xhhao.insight.notification;

import com.xhhao.insight.alert.AlertRule;
import com.xhhao.insight.storage.model.AlertIncident;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.core.extension.notification.Notification;
import run.halo.app.core.extension.notification.Reason;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.notification.ReasonAttributes;

/** Synchronizes Insight acknowledgement state with Halo's native notification center. */
@Component
public class HaloNotificationReadService {

    private static final Duration REACTIVE_TIMEOUT = Duration.ofSeconds(15);
    private static final String NOTIFICATION_TITLE_PREFIX = "[运行洞察] ";
    private static final String LEGACY_NOTIFICATION_TITLE_PREFIX = "[Halo Insight] ";
    private static final Pattern INCIDENT_MARKER = Pattern.compile(
        "<!--\\s*insight-incident:([^\\r\\n]*?)\\s*-->"
    );

    private final ReactiveExtensionClient extensionClient;
    private final Clock clock;

    @Autowired
    public HaloNotificationReadService(ReactiveExtensionClient extensionClient) {
        this(extensionClient, Clock.systemUTC());
    }

    HaloNotificationReadService(ReactiveExtensionClient extensionClient, Clock clock) {
        this.extensionClient = Objects.requireNonNull(extensionClient,
            "extensionClient must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /** Marks every unread Halo notification belonging to the acknowledged incident as read. */
    public int markIncidentRead(AlertIncident incident) {
        Objects.requireNonNull(incident, "incident must not be null");
        return markIncidentsRead(List.of(incident));
    }

    /** Marks unread Halo notifications for a bounded set of acknowledged incidents as read. */
    public int markIncidentsRead(List<AlertIncident> incidents) {
        List<AlertIncident> targets = List.copyOf(
            Objects.requireNonNull(incidents, "incidents must not be null"));
        if (targets.isEmpty()) {
            return 0;
        }
        if (Schedulers.isInNonBlockingThread()) {
            throw new IllegalStateException(
                "Halo notifications cannot be updated synchronously on a Reactor event loop");
        }
        Integer updated = matchingReasonNames(targets)
            .flatMap(reasonNames -> markNotificationsRead(targets, reasonNames,
                Instant.ofEpochMilli(clock.millis())))
            .block(REACTIVE_TIMEOUT);
        return updated == null ? 0 : updated;
    }

    private Mono<Set<String>> matchingReasonNames(List<AlertIncident> incidents) {
        return extensionClient.listAll(
                Reason.class,
                ListOptions.builder().build(),
                Sort.unsorted()
            )
            .filter(reason -> incidents.stream()
                .anyMatch(incident -> belongsToIncident(reason, incident)))
            .map(reason -> reason.getMetadata().getName())
            .filter(Objects::nonNull)
            .collect(HashSet::new, Set::add);
    }

    private Mono<Integer> markNotificationsRead(
        List<AlertIncident> incidents,
        Set<String> reasonNames,
        Instant readAt
    ) {
        return extensionClient.listAll(
                Notification.class,
                ListOptions.builder().build(),
                Sort.unsorted()
            )
            .filter(HaloNotificationReadService::isUnread)
            .filter(notification -> belongsToIncident(notification, incidents, reasonNames))
            .concatMap(notification -> {
                notification.getSpec().setUnread(false);
                notification.getSpec().setLastReadAt(readAt);
                return extensionClient.update(notification).thenReturn(1);
            })
            .reduce(0, Integer::sum);
    }

    private static boolean belongsToIncident(Reason reason, AlertIncident incident) {
        if (reason == null || reason.getSpec() == null
            || !HaloNotificationChannel.REASON_TYPE.equals(reason.getSpec().getReasonType())) {
            return false;
        }
        ReasonAttributes attributes = reason.getSpec().getAttributes();
        if (attributes == null) {
            return false;
        }
        String incidentId = stringValue(attributes.get("incidentId"));
        if (!incidentId.isBlank()) {
            return incident.id().equals(incidentId);
        }
        return legacyReasonBelongsToIncident(attributes, incident);
    }

    private static boolean legacyReasonBelongsToIncident(
        ReasonAttributes attributes,
        AlertIncident incident
    ) {
        String expectedMetric = AlertRule.fromRuleId(incident.ruleId())
            .map(AlertRule::metricKey)
            .orElse("");
        if (expectedMetric.isBlank()
            || !expectedMetric.equals(stringValue(attributes.get("metric")))) {
            return false;
        }
        try {
            long occurredAtMs = Instant.parse(stringValue(attributes.get("occurredAt")))
                .toEpochMilli();
            return occurredAtMs >= incident.triggeredAtMs()
                && occurredAtMs <= incident.updatedAtMs();
        } catch (DateTimeParseException error) {
            return false;
        }
    }

    private static boolean belongsToIncident(
        Notification notification,
        List<AlertIncident> incidents,
        Set<String> reasonNames
    ) {
        Notification.NotificationSpec spec = notification.getSpec();
        Optional<String> markedIncidentId = incidentIdFromHtml(spec.getHtmlContent());
        if (markedIncidentId.isPresent()) {
            return incidents.stream()
                .anyMatch(incident -> incident.id().equals(markedIncidentId.get()));
        }
        if (reasonNames.contains(spec.getReason())) {
            return true;
        }
        return incidents.stream()
            .anyMatch(incident -> legacyNotificationBelongsToIncident(notification, incident));
    }

    private static Optional<String> incidentIdFromHtml(String htmlContent) {
        if (htmlContent == null || htmlContent.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = INCIDENT_MARKER.matcher(htmlContent);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String incidentId = matcher.group(1).trim();
        return incidentId.isEmpty() ? Optional.empty() : Optional.of(incidentId);
    }

    private static boolean legacyNotificationBelongsToIncident(
        Notification notification,
        AlertIncident incident
    ) {
        Instant createdAt = notification.getMetadata() == null
            ? null : notification.getMetadata().getCreationTimestamp();
        if (createdAt == null) {
            return false;
        }
        long createdAtMs = createdAt.toEpochMilli();
        if (createdAtMs < incident.triggeredAtMs() || createdAtMs > incident.updatedAtMs()) {
            return false;
        }
        String title = stringValue(notification.getSpec().getTitle()).trim();
        return title.equals(incident.title())
            || title.equals(NOTIFICATION_TITLE_PREFIX + incident.title())
            || title.equals(LEGACY_NOTIFICATION_TITLE_PREFIX + incident.title());
    }

    private static boolean isUnread(Notification notification) {
        return notification != null
            && notification.getSpec() != null
            && notification.getSpec().isUnread();
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }
}
