package com.xhhao.insight.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xhhao.insight.config.InsightSettingsService;
import com.xhhao.insight.config.NotificationSettings;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.notification.Subscription;
import run.halo.app.notification.NotificationCenter;
import run.halo.app.notification.NotificationReasonEmitter;
import run.halo.app.notification.ReasonPayload;

@ExtendWith(MockitoExtension.class)
class HaloNotificationChannelTest {

    @Mock
    InsightSettingsService settingsService;

    @Mock
    NotificationCenter notificationCenter;

    @Mock
    NotificationReasonEmitter reasonEmitter;

    private NotificationSettings settings;
    private HaloNotificationChannel channel;

    @BeforeEach
    void setUp() {
        settings = new NotificationSettings();
        when(settingsService.notifications()).thenReturn(settings);
        channel = new HaloNotificationChannel(settingsService, notificationCenter, reasonEmitter);
    }

    @Test
    void reportsDisabledWithoutCallingHalo() {
        NotificationResult result = channel.send(notification(false));

        assertThat(result).isEqualTo(new NotificationResult(
            "halo", false, "Halo 站内通知未启用"));
        verify(notificationCenter, never()).subscribe(any(), any());
        verify(reasonEmitter, never()).emit(any(), any());
    }

    @Test
    void reportsMissingRecipientsSeparately() {
        settings.setHaloNotificationEnabled(true);
        settings.setHaloNotificationRecipients(List.of(" ", "\t"));

        NotificationResult result = channel.send(notification(false));

        assertThat(result).isEqualTo(new NotificationResult(
            "halo", false, "未配置 Halo 站内通知接收人"));
        verify(notificationCenter, never()).subscribe(any(), any());
        verify(reasonEmitter, never()).emit(any(), any());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void subscribesDistinctUsersAndEmitsTargetedReasons() {
        settings.setHaloNotificationEnabled(true);
        settings.setHaloNotificationRecipients(
            List.of(" admin ", "admin", "operator", " "));
        when(notificationCenter.subscribe(any(), any()))
            .thenReturn(Mono.just(new Subscription()));
        when(reasonEmitter.emit(eq(HaloNotificationChannel.REASON_TYPE), any()))
            .thenReturn(Mono.empty());

        NotificationResult result = channel.send(notification(false));

        assertThat(result).isEqualTo(new NotificationResult(
            "halo", true, "已提交给 2 位 Halo 用户"));

        ArgumentCaptor<Subscription.Subscriber> subscriberCaptor =
            ArgumentCaptor.forClass(Subscription.Subscriber.class);
        ArgumentCaptor<Subscription.InterestReason> reasonCaptor =
            ArgumentCaptor.forClass(Subscription.InterestReason.class);
        verify(notificationCenter, times(2))
            .subscribe(subscriberCaptor.capture(), reasonCaptor.capture());
        assertThat(subscriberCaptor.getAllValues())
            .extracting(Subscription.Subscriber::getName)
            .containsExactly("admin", "operator");
        assertThat(reasonCaptor.getAllValues())
            .extracting(Subscription.InterestReason::getReasonType)
            .containsOnly(HaloNotificationChannel.REASON_TYPE);
        assertThat(reasonCaptor.getAllValues())
            .extracting(Subscription.InterestReason::getExpression)
            .containsExactly("props.targetUser == 'admin'", "props.targetUser == 'operator'");

        ArgumentCaptor<Consumer<ReasonPayload.ReasonPayloadBuilder>> payloadCaptor =
            ArgumentCaptor.forClass((Class) Consumer.class);
        verify(reasonEmitter, times(2))
            .emit(eq(HaloNotificationChannel.REASON_TYPE), payloadCaptor.capture());
        List<ReasonPayload> payloads = payloadCaptor.getAllValues().stream()
            .map(consumer -> {
                var builder = ReasonPayload.builder();
                consumer.accept(builder);
                return builder.build();
            })
            .toList();
        assertThat(payloads)
            .extracting(payload -> payload.getAttributes().get("targetUser"))
            .containsExactly("admin", "operator");
        assertThat(payloads)
            .allSatisfy(payload -> {
                assertThat(payload.getAttributes())
                    .containsEntry("stateLabel", "告警中")
                    .containsEntry("metric", "system.cpu")
                    .containsEntry("value", 91d)
                    .containsEntry("threshold", 85d)
                    .containsEntry("consoleUrl", "/console/insight");
                assertThat(payload.getSubject().getName()).isEqualTo("insight");
                assertThat(payload.getSubject().getUrl()).isEqualTo("/console/insight");
            });
    }

    @Test
    void propagatesReactiveFailureToDispatcher() {
        settings.setHaloNotificationEnabled(true);
        settings.setHaloNotificationRecipients(List.of("admin"));
        when(notificationCenter.subscribe(any(), any()))
            .thenReturn(Mono.error(new IllegalStateException("subscription unavailable")));
        when(reasonEmitter.emit(eq(HaloNotificationChannel.REASON_TYPE), any()))
            .thenReturn(Mono.empty());

        assertThatThrownBy(() -> channel.send(notification(true)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("subscription unavailable");
    }

    private static AlertNotification notification(boolean recovered) {
        return new AlertNotification(
            recovered ? "CPU 已恢复" : "CPU 使用率过高",
            "warning",
            recovered ? "CPU 使用率已回落" : "CPU 使用率持续超过阈值",
            "system.cpu",
            91,
            85,
            Instant.parse("2026-09-02T01:00:00Z"),
            recovered
        );
    }
}
