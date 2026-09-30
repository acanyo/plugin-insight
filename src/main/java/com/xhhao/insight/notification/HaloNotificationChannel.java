package com.xhhao.insight.notification;

import com.xhhao.insight.config.InsightSettingsService;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.core.extension.notification.Reason;
import run.halo.app.core.extension.notification.Subscription;
import run.halo.app.notification.NotificationCenter;
import run.halo.app.notification.NotificationReasonEmitter;
import run.halo.app.notification.UserIdentity;

/**
 * Sends Insight alerts to Halo's native notification center.
 *
 * <p>{@link NotificationChannel} is intentionally synchronous. The dispatcher invokes channels
 * on its dedicated worker pool, so this adapter may wait for the reactive subscription and emit
 * operations without occupying a Reactor event-loop thread.</p>
 */
@Component
public class HaloNotificationChannel implements NotificationChannel {

    static final String REASON_TYPE = "insight-alert-state-changed";
    static final String CHANNEL_NAME = "halo";
    private static final String CONSOLE_URL = "/console/insight";
    private static final Duration REACTIVE_TIMEOUT = Duration.ofSeconds(15);

    private final InsightSettingsService settingsService;
    private final NotificationCenter notificationCenter;
    private final NotificationReasonEmitter reasonEmitter;

    public HaloNotificationChannel(
        InsightSettingsService settingsService,
        NotificationCenter notificationCenter,
        NotificationReasonEmitter reasonEmitter
    ) {
        this.settingsService = settingsService;
        this.notificationCenter = notificationCenter;
        this.reasonEmitter = reasonEmitter;
    }

    @Override
    public String name() {
        return CHANNEL_NAME;
    }

    @Override
    public synchronized NotificationResult send(AlertNotification notification) {
        var settings = settingsService.notifications();
        if (!settings.isHaloNotificationEnabled()) {
            return NotificationResult.skipped(name(), "Halo 站内通知未启用");
        }

        List<String> recipients = normalizeRecipients(settings.getHaloNotificationRecipients());
        if (recipients.isEmpty()) {
            return NotificationResult.skipped(name(), "未配置 Halo 站内通知接收人");
        }

        await(Flux.fromIterable(recipients)
            .concatMap(recipient -> subscribeAndEmit(recipient, notification))
            .then());
        return NotificationResult.sent(name(), "已提交给 " + recipients.size() + " 位 Halo 用户");
    }

    private Mono<Void> subscribeAndEmit(String recipient, AlertNotification notification) {
        var subscriber = new Subscription.Subscriber();
        subscriber.setName(recipient);

        var interestReason = new Subscription.InterestReason();
        interestReason.setReasonType(REASON_TYPE);
        interestReason.setExpression("props.targetUser == '%s'".formatted(
            escapeExpressionLiteral(recipient)));

        return notificationCenter.subscribe(subscriber, interestReason)
            .then(reasonEmitter.emit(REASON_TYPE, builder -> builder
                .author(UserIdentity.of("insight"))
                .subject(Reason.Subject.builder()
                    .apiVersion("plugin.halo.run/v1alpha1")
                    .kind("Plugin")
                    .name("insight")
                    .title(displayTitle(notification.title()))
                    .url(CONSOLE_URL)
                    .build())
                .attribute("targetUser", recipient)
                .attribute("incidentId", safe(notification.incidentId()))
                .attribute("title", safe(notification.title()))
                .attribute("severity", safe(notification.severity()))
                .attribute("message", safe(notification.message()))
                .attribute("metric", safe(notification.metric()))
                .attribute("value", notification.value())
                .attribute("threshold", notification.threshold())
                .attribute("occurredAt", notification.occurredAt() == null
                    ? "" : notification.occurredAt().toString())
                .attribute("recovered", notification.recovered())
                .attribute("stateLabel", notification.recovered() ? "已恢复" : "告警中")
                .attribute("consoleUrl", CONSOLE_URL)));
    }

    private static List<String> normalizeRecipients(List<String> configured) {
        if (configured == null || configured.isEmpty()) {
            return List.of();
        }
        var normalized = new LinkedHashSet<String>();
        for (String recipient : configured) {
            if (recipient != null && !recipient.isBlank()) {
                normalized.add(recipient.trim());
            }
        }
        return List.copyOf(normalized);
    }

    private static String escapeExpressionLiteral(String value) {
        return value.replace("'", "''");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String displayTitle(String value) {
        return value == null || value.isBlank() ? "Halo Insight 运行洞察通知" : value;
    }

    private static void await(Mono<Void> operation) {
        if (Schedulers.isInNonBlockingThread()) {
            throw new IllegalStateException(
                "Halo 站内通知不能在 Reactor 事件循环线程中同步发送");
        }
        operation.block(REACTIVE_TIMEOUT);
    }
}
