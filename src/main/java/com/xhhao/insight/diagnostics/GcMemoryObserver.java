package com.xhhao.insight.diagnostics;

import com.sun.management.GarbageCollectionNotificationInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;
import org.springframework.stereotype.Component;

/** Reads actual GC completion notifications; never requests collection. */
@Component
public class GcMemoryObserver {
    private final ArrayDeque<Point> points = new ArrayDeque<>();
    private final List<NotificationEmitter> emitters = new ArrayList<>();
    private final NotificationListener listener = (notification, ignored) -> {
        if (GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(notification.getType())) {
            try { accept(GarbageCollectionNotificationInfo.from((CompositeData) notification.getUserData())); }
            catch (RuntimeException ignoredError) { /* Optional JVM capability. */ }
        }
    };
    private Set<String> heapPools = Set.of();

    public synchronized void start() {
        stop(); points.clear();
        heapPools = ManagementFactory.getMemoryPoolMXBeans().stream()
            .filter(pool -> pool.getType() == MemoryType.HEAP).map(pool -> pool.getName()).collect(Collectors.toSet());
        for (var bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (bean instanceof NotificationEmitter emitter) {
                try { emitter.addNotificationListener(listener, null, null); emitters.add(emitter); }
                catch (RuntimeException ignored) { /* Capability reported by status. */ }
            }
        }
    }

    public synchronized void stop() {
        for (var emitter : emitters) {
            try { emitter.removeNotificationListener(listener); } catch (Exception ignored) { }
        }
        emitters.clear();
    }

    private synchronized void accept(GarbageCollectionNotificationInfo event) {
        long now = ManagementFactory.getRuntimeMXBean().getStartTime() + event.getGcInfo().getEndTime();
        long used = event.getGcInfo().getMemoryUsageAfterGc().entrySet().stream()
            .filter(entry -> heapPools.contains(entry.getKey())).mapToLong(entry -> entry.getValue().getUsed()).sum();
        // Keep one minimum per minute so frequent young collections cannot flood the history.
        long minute = now / 60_000 * 60_000;
        var previous = points.peekLast();
        if (previous != null && previous.atMs() == minute) {
            points.removeLast(); used = Math.min(previous.usedBytes(), used);
        }
        points.addLast(new Point(minute, used));
        while (points.size() > 120 || points.peekFirst().atMs() < now - 7_200_000) { points.removeFirst(); }
    }

    public synchronized MemoryTrend snapshot(long now) {
        var recent = points.stream().filter(p -> p.atMs() >= now - 3_600_000).toList();
        boolean enough = recent.size() >= 10
            && recent.getLast().atMs() - recent.getFirst().atMs() >= 15 * 60_000
            && now - recent.getLast().atMs() < 5 * 60_000;
        long growth = recent.size() < 2 ? 0 : recent.getLast().usedBytes() - recent.getFirst().usedBytes();
        long rises = 0;
        for (int i = 1; i < recent.size(); i++) {
            if (recent.get(i).usedBytes() > recent.get(i - 1).usedBytes()) { rises++; }
        }
        boolean growing = enough && growth > 32 * 1024 * 1024L && rises >= (recent.size() - 1) * .7;
        return new MemoryTrend(!emitters.isEmpty(), enough, growing, growth, recent,
            "GC 完成后堆占用的分钟最低值；年轻代回收不等于全堆存活量，持续增长仅是排查线索，不能证明泄漏或归因到插件。");
    }

    public record Point(long atMs, long usedBytes) {}
    public record MemoryTrend(boolean available, boolean sufficient, boolean sustainedGrowth,
                              long growthBytes, List<Point> points, String explanation) {}
}
