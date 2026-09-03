package com.xhhao.insight.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class NotificationDispatcherTest {

    @Test
    void shouldNotExposeChannelExceptionMessageToApiCaller() throws Exception {
        NotificationChannel failing = new NotificationChannel() {
            @Override
            public String name() {
                return "email";
            }

            @Override
            public NotificationResult send(AlertNotification ignored) {
                throw new IllegalStateException(
                    "535 authentication failed for secret-user@smtp.internal");
            }
        };
        NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(failing));
        try {
            List<NotificationResult> results = dispatcher.dispatch(notification())
                .get(2, TimeUnit.SECONDS);

            assertThat(results).containsExactly(
                NotificationResult.skipped("email", "通知发送失败"));
            assertThat(results.getFirst().message())
                .doesNotContain("secret-user", "smtp.internal", "535");
        } finally {
            dispatcher.destroy();
        }
    }

    private static AlertNotification notification() {
        return new AlertNotification(
            "test", "info", "test", "notification.test", 1, 1,
            Instant.parse("2026-09-02T01:00:00Z"), false);
    }
}
