package com.xhhao.insight.notification;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NotificationDispatcher implements DisposableBean {

    private final List<NotificationChannel> channels;
    private final ExecutorService executor = Executors.newFixedThreadPool(2,
        Thread.ofPlatform().name("insight-notification-", 0).factory());

    public NotificationDispatcher(List<NotificationChannel> channels) {
        this.channels = List.copyOf(channels);
    }

    public CompletableFuture<List<NotificationResult>> dispatch(AlertNotification notification) {
        return CompletableFuture.supplyAsync(() -> channels.stream()
            .map(channel -> send(channel, notification))
            .toList(), executor);
    }

    private NotificationResult send(NotificationChannel channel, AlertNotification notification) {
        try {
            return channel.send(notification);
        } catch (Exception error) {
            log.warn("Insight notification channel {} failed ({})",
                channel.name(), error.getClass().getSimpleName());
            return NotificationResult.skipped(channel.name(), "通知发送失败");
        }
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }
}
