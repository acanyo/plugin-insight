package com.xhhao.insight.api;

import com.xhhao.insight.notification.AlertNotification;
import com.xhhao.insight.notification.NotificationDispatcher;
import com.xhhao.insight.notification.NotificationResult;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Sends an operator-requested notification probe through all configured channels. */
@Component
public class NotificationCommandService {

    private final NotificationDispatcher notifications;
    private final Clock clock;

    @Autowired
    public NotificationCommandService(NotificationDispatcher notifications) {
        this(notifications, Clock.systemUTC());
    }

    NotificationCommandService(NotificationDispatcher notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    public CompletableFuture<List<NotificationResult>> testNotifications() {
        double value = 1;
        return notifications.dispatch(new AlertNotification(
            "Insight 通知测试", "info", "这是一条 Halo Insight 测试通知。",
            "notification.test", value, value, Instant.ofEpochMilli(clock.millis()), false
        ));
    }
}
