package com.xhhao.insight.notification;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NotificationDispatcher implements DisposableBean {

    private final List<NotificationChannel> channels;
    private final DeliveryRepository deliveries;
    private final AtomicInteger pendingCommands = new AtomicInteger();
    private ScheduledThreadPoolExecutor executor;

    public NotificationDispatcher(List<NotificationChannel> channels, DeliveryRepository deliveries) {
        this.channels = List.copyOf(channels);
        this.deliveries = deliveries;
    }

    public synchronized void start() {
        if (executor != null && !executor.isShutdown()) { return; }
        executor = new ScheduledThreadPoolExecutor(1, Thread.ofPlatform().name("insight-delivery-", 0).factory());
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        executor.scheduleWithFixedDelay(this::retryDue, 10, 10, TimeUnit.SECONDS);
    }

    public synchronized CompletableFuture<List<NotificationResult>> dispatch(AlertNotification notification) {
        return dispatch(notification, null);
    }

    /** Dispatches a notification through one named channel without relaying it to other channels. */
    public synchronized CompletableFuture<List<NotificationResult>> dispatchTo(
        AlertNotification notification, String channel
    ) {
        return dispatch(notification, Set.of(channel));
    }

    private CompletableFuture<List<NotificationResult>> dispatch(
        AlertNotification notification, Set<String> selectedChannels
    ) {
        if (executor == null || executor.isShutdown()) {
            return CompletableFuture.failedFuture(new IllegalStateException("通知工作线程未运行"));
        }
        if (pendingCommands.incrementAndGet() > 100) {
            pendingCommands.decrementAndGet();
            return CompletableFuture.failedFuture(new IllegalStateException("通知工作队列已满"));
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                var result = new ArrayList<NotificationResult>();
                for (var channel : channels) {
                    if (selectedChannels != null && !selectedChannels.contains(channel.name())) {
                        continue;
                    }
                    DeliveryRepository.Delivery delivery;
                    try { delivery = deliveries.enqueue(channel.name(), notification, System.currentTimeMillis()); }
                    catch (RuntimeException unavailable) {
                        try {
                            var sent = channel.send(notification);
                            result.add(new NotificationResult(channel.name(), sent.sent(), sent.message() + "；未持久化，无法保证重试"));
                        } catch (Exception failed) {
                            result.add(NotificationResult.skipped(channel.name(), "发送失败且无法持久化重试"));
                        }
                        log.warn("Insight delivery history unavailable for {}", channel.name());
                        continue;
                    }
                    result.add(send(delivery));
                }
                return List.copyOf(result);
            } finally { pendingCommands.decrementAndGet(); }
        }, executor);
    }

    private NotificationResult send(DeliveryRepository.Delivery delivery) {
        int attempts = delivery.attempts() + 1;
        try {
            var channel = channels.stream().filter(c -> c.name().equals(delivery.channel())).findFirst().orElseThrow();
            var result = channel.send(deliveries.notification(delivery));
            deliveries.finish(delivery, result.sent() ? "sent" : "skipped", attempts, 0, result.message());
            return result;
        } catch (Exception failure) {
            long now = System.currentTimeMillis();
            boolean exhausted = attempts >= 5 || now - delivery.createdAtMs() > TimeUnit.DAYS.toMillis(1);
            String message = exhausted ? "投递失败，已达到重试上限" : "投递失败，将自动重试";
            deliveries.finish(delivery, exhausted ? "failed" : "pending", attempts, now + retryDelayMs(attempts), message);
            return NotificationResult.skipped(delivery.channel(), message);
        }
    }

    public static long retryDelayMs(int attempts) {
        return Math.min(900_000L, 30_000L * (1L << Math.clamp(attempts - 1, 0, 5)));
    }

    public synchronized void acknowledge(String incidentId) {
        if (executor != null && !executor.isShutdown()) {
            executor.execute(() -> {
                try { deliveries.acknowledge(incidentId); }
                catch (RuntimeException error) { log.warn("Unable to cancel acknowledged notification retries"); }
            });
        }
    }

    private void retryDue() {
        try {
            for (var delivery : deliveries.list(true, System.currentTimeMillis(), 20)) {
                if (Thread.currentThread().isInterrupted()) { return; }
                send(delivery);
            }
        } catch (RuntimeException error) { log.debug("Insight delivery retry deferred ({})", error.getClass().getSimpleName()); }
    }

    @Override
    public synchronized void destroy() {
        if (executor != null) { executor.shutdownNow(); }
    }
}
